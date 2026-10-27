package com.guangqian.springkb.manage;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.guangqian.springkb.common.NotFoundException;
import com.guangqian.springkb.crawler.HeadingNode;
import com.guangqian.springkb.crawler.HtmlToMarkdown;
import com.guangqian.springkb.crawler.ImageRef;
import com.guangqian.springkb.crawler.RawDocument;
import com.guangqian.springkb.dto.AdminImportResult;
import com.guangqian.springkb.dto.AdminOverview;
import com.guangqian.springkb.dto.CategoryCreateRequest;
import com.guangqian.springkb.dto.CategoryDto;
import com.guangqian.springkb.dto.DocumentDto;
import com.guangqian.springkb.dto.ImportRequest;
import com.guangqian.springkb.dto.MemberDto;
import com.guangqian.springkb.dto.MetaUpdateRequest;
import com.guangqian.springkb.dto.PageResult;
import com.guangqian.springkb.ingestion.IngestionService;
import com.guangqian.springkb.ingestion.ImportResult;
import com.guangqian.springkb.ingestion.ImportSummary;
import com.guangqian.springkb.kb.ScanAuditService;
import com.guangqian.springkb.kb.ScanRuleService;
import com.guangqian.springkb.kb.SensitiveScanException;
import com.guangqian.springkb.kb.SensitiveScanService;
import com.guangqian.springkb.kb.UgcService;
import com.guangqian.springkb.repository.UserRepository;
import com.guangqian.springkb.service.AuthService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 管理端核心服务（§5.3）：文档列表（分页/过滤）、单文档导入 / data/raw 批量导入、
 * 版本状态切换（kb_document.active + vector_store.metadata.active 双写）、删除与 chunk 列表。
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private static final List<String> DISK_SOURCES = List.of("vue", "springai", "springboot");

    /** 管理端 URL 抓取的域名白名单（防 SSRF）：仅允许官方文档站。 */
    private static final List<String> ALLOWED_HOST_SUFFIXES = List.of("vuejs.org", "spring.io");

    private static final int MAX_PAGE_SIZE = 200;

    private static final TypeReference<List<RawDocument>> RAW_DOC_LIST = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final IngestionService ingestionService;
    private final JsonMapper jsonMapper;
    private final UserRepository userRepository;
    private final ScanRuleService scanRuleService;
    private final ScanAuditService scanAuditService;

    public AdminService(JdbcTemplate jdbcTemplate, IngestionService ingestionService, JsonMapper jsonMapper,
                        UserRepository userRepository, ScanRuleService scanRuleService,
                        ScanAuditService scanAuditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.ingestionService = ingestionService;
        this.jsonMapper = jsonMapper;
        this.userRepository = userRepository;
        this.scanRuleService = scanRuleService;
        this.scanAuditService = scanAuditService;
    }

    // ---------- 1. 文档列表：过滤（product/source/version/active/keyword）+ 分页 ----------

    /** 分页列表：page 从 1 起、size 钳制 1~200。 */
    public PageResult<DocumentDto> page(String product, String source, String version, String active, String keyword,
            int page, int size) {
        Where w = buildWhere(product, source, version, active, keyword);
        long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM kb_document " + w.whereOnly(), Long.class, w.args().toArray());
        int p = Math.max(1, page);
        int s = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        List<Object> args = new ArrayList<>(w.args());
        args.add(s);
        args.add((long) (p - 1) * s);
        List<DocumentDto> list = jdbcTemplate.query(w.sql() + " LIMIT ? OFFSET ?", DOCUMENT_ROW_MAPPER, args.toArray());
        return new PageResult<>(list, total, p, s);
    }

    /** 文档总数 / 启用状态 / chunk / 新鲜度 / 可见性 / 新增文档，一条聚合 SQL。 */
    private static final String CONTENT_AGG_SQL = """
            SELECT count(*) AS total,
                   count(*) FILTER (WHERE active) AS active,
                   count(*) FILTER (WHERE NOT active) AS inactive,
                   COALESCE(sum(chunk_count), 0) AS chunks,
                   COALESCE(avg(chunk_count), 0) AS avg_chunks,
                   count(*) FILTER (WHERE chunk_count = 0) AS empty_chunks,
                   count(*) FILTER (WHERE visibility = 'team') AS team_docs,
                   count(*) FILTER (WHERE visibility = 'private') AS private_docs,
                   count(*) FILTER (WHERE last_modified >= now() - interval '30 days') AS fresh_30,
                   count(*) FILTER (WHERE last_modified < now() - interval '30 days'
                                     AND last_modified >= now() - interval '180 days') AS fresh_180,
                   count(*) FILTER (WHERE last_modified < now() - interval '180 days'
                                     AND last_modified >= now() - interval '365 days') AS fresh_365,
                   count(*) FILTER (WHERE last_modified < now() - interval '365 days') AS fresh_old,
                   count(*) FILTER (WHERE last_modified IS NULL) AS fresh_unknown,
                   COALESCE(avg(EXTRACT(EPOCH FROM (now() - COALESCE(last_modified, fetched_at))) / 86400.0), 0)
                       AS avg_age_days,
                   count(*) FILTER (WHERE created_at::date = current_date) AS today_new,
                   count(*) FILTER (WHERE created_at >= now() - CAST(? AS int) * interval '1 day') AS window_new,
                   count(*) FILTER (WHERE created_at >= now() - CAST(? AS int) * interval '1 day'
                                     AND created_at < now() - CAST(? AS int) * interval '1 day')
                       AS prev_window_new
            FROM kb_document
            """;

    /**
     * 概览聚合（GET /api/admin/overview）：days 仅接受 7/30，其余按 7 处理。
     * 全部指标由聚合 / JOIN SQL 得出，不把文档行拉进 Java 遍历。
     */
    public AdminOverview overview(int days) {
        int windowDays = (days == 7 || days == 30) ? days : 7;
        int previousDays = windowDays * 2;

        ContentAgg content = jdbcTemplate.queryForObject(CONTENT_AGG_SQL, (rs, rowNum) -> new ContentAgg(
                rs.getLong("total"), rs.getLong("active"), rs.getLong("inactive"),
                rs.getLong("chunks"), rs.getDouble("avg_chunks"), rs.getLong("empty_chunks"),
                rs.getLong("team_docs"), rs.getLong("private_docs"),
                rs.getLong("fresh_30"), rs.getLong("fresh_180"), rs.getLong("fresh_365"),
                rs.getLong("fresh_old"), rs.getLong("fresh_unknown"),
                rs.getDouble("avg_age_days"), rs.getLong("today_new"),
                rs.getLong("window_new"), rs.getLong("prev_window_new")),
                windowDays, previousDays, windowDays);

        Map<String, List<AdminOverview.ProductSlice>> groups = loadGroupSlices();
        TopSlice products = topSlice(groups.getOrDefault("product", List.of()));
        TopSlice origins = topSlice(groups.getOrDefault("origin", List.of()));
        TopSlice versions = topSlice(groups.getOrDefault("version", List.of()));

        GovernanceAgg review = jdbcTemplate.queryForObject("""
                SELECT count(*) FILTER (WHERE review_status = 'approved') AS approved,
                       count(*) FILTER (WHERE review_status = 'pending') AS pending,
                       count(*) FILTER (WHERE review_status = 'rejected') AS rejected,
                       COALESCE(avg(EXTRACT(EPOCH FROM (reviewed_at - created_at)) / 3600.0)
                                FILTER (WHERE reviewed_at IS NOT NULL), 0) AS avg_review_hours,
                       COALESCE(max(EXTRACT(EPOCH FROM (now() - updated_at)) / 86400.0)
                                FILTER (WHERE review_status = 'pending'), 0) AS pending_oldest_days,
                       count(*) FILTER (WHERE review_status = 'rejected'
                                         AND updated_at < now() - interval '7 days') AS rejected_stale
                FROM kb_document
                """, (rs, rowNum) -> new GovernanceAgg(
                rs.getLong("approved"), rs.getLong("pending"), rs.getLong("rejected"),
                rs.getDouble("avg_review_hours"), rs.getDouble("pending_oldest_days"),
                rs.getLong("rejected_stale")));

        ScanAgg scan = jdbcTemplate.queryForObject("""
                SELECT (SELECT count(*) FROM kb_notification WHERE NOT read) AS unread,
                       count(*) FILTER (WHERE created_at >= now() - CAST(? AS int) * interval '1 day')
                           AS blocks,
                       count(DISTINCT doc_title)
                           FILTER (WHERE created_at >= now() - CAST(? AS int) * interval '1 day')
                           AS blocked_titles,
                       COALESCE(sum(hit_count)
                                FILTER (WHERE created_at >= now() - CAST(? AS int) * interval '1 day'), 0)
                           AS hits,
                       count(*) FILTER (WHERE created_at >= now() - CAST(? AS int) * interval '1 day'
                                         AND created_at < now() - CAST(? AS int) * interval '1 day')
                           AS prev_blocks
                FROM kb_scan_log
                WHERE created_at >= now() - CAST(? AS int) * interval '1 day'
                """, (rs, rowNum) -> new ScanAgg(
                rs.getLong("unread"), rs.getLong("blocks"), rs.getLong("blocked_titles"),
                rs.getLong("prev_blocks"), rs.getLong("hits")),
                windowDays, windowDays, windowDays, windowDays, windowDays, previousDays);

        List<AdminOverview.RuleHit> rules = new ArrayList<>();
        List<AdminOverview.ActionSlice> actions = new ArrayList<>();
        jdbcTemplate.query("""
                SELECT dim, label, cnt FROM (
                    SELECT 'rule' AS dim, rule_type AS label, count(*) AS cnt
                    FROM kb_scan_log
                    WHERE created_at >= now() - CAST(? AS int) * interval '1 day'
                    GROUP BY rule_type
                    UNION ALL
                    SELECT 'action', action, count(*)
                    FROM kb_scan_log
                    WHERE created_at >= now() - CAST(? AS int) * interval '1 day'
                    GROUP BY action
                ) g
                ORDER BY dim, cnt DESC, label
                """, (RowCallbackHandler) rs -> {
            if ("rule".equals(rs.getString("dim")) && rules.size() < 5) {
                rules.add(new AdminOverview.RuleHit(rs.getString("label"), rs.getLong("cnt")));
            } else if ("action".equals(rs.getString("dim"))) {
                actions.add(new AdminOverview.ActionSlice(rs.getString("label"), rs.getLong("cnt")));
            }
        }, windowDays, windowDays);

        QaAgg qa = jdbcTemplate.queryForObject("""
                SELECT (SELECT count(*) FROM kb_conversation) AS conversations,
                       (SELECT count(*) FROM kb_message) AS messages,
                       (SELECT count(*) FROM kb_message
                         WHERE created_at >= now() - CAST(? AS int) * interval '1 day') AS window_qa,
                       (SELECT count(*) FROM kb_message
                         WHERE created_at >= now() - CAST(? AS int) * interval '1 day'
                           AND created_at < now() - CAST(? AS int) * interval '1 day') AS prev_qa,
                       count(*) FILTER (WHERE found) AS window_found,
                       COALESCE(avg(CASE WHEN jsonb_typeof(sources) = 'array'
                                         THEN jsonb_array_length(sources) ELSE 0 END), 0) AS avg_sources,
                       (SELECT count(DISTINCT c.username)
                          FROM kb_message m JOIN kb_conversation c ON c.id = m.conversation_id
                         WHERE m.created_at >= now() - CAST(? AS int) * interval '1 day') AS active_askers
                FROM kb_message
                WHERE created_at >= now() - CAST(? AS int) * interval '1 day'
                """, (rs, rowNum) -> new QaAgg(
                rs.getLong("conversations"), rs.getLong("messages"), rs.getLong("window_qa"),
                rs.getLong("prev_qa"), rs.getLong("window_found"), rs.getDouble("avg_sources"),
                rs.getLong("active_askers")),
                windowDays, previousDays, windowDays, windowDays, windowDays);

        List<AdminOverview.BlindSpot> blindspots = jdbcTemplate.query("""
                SELECT left(question, 60) AS q, count(*) AS cnt
                FROM kb_message
                WHERE found = false AND created_at >= now() - CAST(? AS int) * interval '1 day'
                GROUP BY left(question, 60)
                ORDER BY cnt DESC, q
                LIMIT 5
                """, (rs, rowNum) -> new AdminOverview.BlindSpot(rs.getString("q"), rs.getLong("cnt")),
                windowDays);

        AdminOverview.TrendSeries trend = loadTrend(windowDays);

        List<UserRoleAgg> roleRows = jdbcTemplate.query("""
                SELECT role, count(*) AS cnt,
                       count(*) FILTER (WHERE created_at >= now() - CAST(? AS int) * interval '1 day')
                           AS new_cnt,
                       count(*) FILTER (WHERE created_at >= now() - CAST(? AS int) * interval '1 day'
                                         AND created_at < now() - CAST(? AS int) * interval '1 day')
                           AS prev_new_cnt
                FROM kb_user
                GROUP BY role
                ORDER BY cnt DESC, role
                """, (rs, rowNum) -> new UserRoleAgg(
                rs.getString("role"), rs.getLong("cnt"), rs.getLong("new_cnt"),
                rs.getLong("prev_new_cnt")), windowDays, previousDays, windowDays);
        long totalUsers = roleRows.stream().mapToLong(UserRoleAgg::count).sum();
        long admins = roleRows.stream().filter(r -> AuthService.ROLE_ADMIN.equals(r.role()))
                .mapToLong(UserRoleAgg::count).sum();
        long members = roleRows.stream().filter(r -> AuthService.ROLE_MEMBER.equals(r.role()))
                .mapToLong(UserRoleAgg::count).sum();
        long newUsers = roleRows.stream().mapToLong(UserRoleAgg::newCount).sum();
        long prevNewUsers = roleRows.stream().mapToLong(UserRoleAgg::prevNewCount).sum();
        List<AdminOverview.RoleSlice> roles = roleRows.stream()
                .map(r -> new AdminOverview.RoleSlice(r.role(), r.count()))
                .toList();

        Long activeUsers = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM (
                    SELECT c.username
                    FROM kb_message m JOIN kb_conversation c ON c.id = m.conversation_id
                    WHERE m.created_at >= now() - CAST(? AS int) * interval '1 day'
                    UNION
                    SELECT author FROM kb_document
                    WHERE author IS NOT NULL AND created_at >= now() - CAST(? AS int) * interval '1 day'
                ) u
                """, Long.class, windowDays, windowDays);

        Long prevActiveUsers = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM (
                    SELECT c.username
                    FROM kb_message m JOIN kb_conversation c ON c.id = m.conversation_id
                    WHERE m.created_at >= now() - CAST(? AS int) * interval '1 day'
                      AND m.created_at < now() - CAST(? AS int) * interval '1 day'
                    UNION
                    SELECT author FROM kb_document
                    WHERE author IS NOT NULL
                      AND created_at >= now() - CAST(? AS int) * interval '1 day'
                      AND created_at < now() - CAST(? AS int) * interval '1 day'
                ) u
                """, Long.class, previousDays, windowDays, previousDays, windowDays);

        List<AdminOverview.ContributorSlice> contributors = jdbcTemplate.query("""
                SELECT author AS username, count(*) AS cnt
                FROM kb_document
                WHERE author IS NOT NULL
                GROUP BY author
                ORDER BY cnt DESC, author
                LIMIT 5
                """, (rs, rowNum) -> new AdminOverview.ContributorSlice(rs.getString("username"), rs.getLong("cnt")));

        EngagementAgg engagement = jdbcTemplate.queryForObject("""
                SELECT (SELECT count(*) FROM kb_doc_like) AS likes,
                       (SELECT count(*) FROM kb_doc_favorite) AS favorites
                """, (rs, rowNum) -> new EngagementAgg(rs.getLong("likes"), rs.getLong("favorites")));
        List<AdminOverview.FavoriteSlice> topFavorites = jdbcTemplate.query("""
                SELECT f.doc_id, COALESCE(d.title, '') AS title, count(*) AS cnt
                FROM kb_doc_favorite f LEFT JOIN kb_document d ON d.id = f.doc_id
                GROUP BY f.doc_id, d.title
                ORDER BY cnt DESC, f.doc_id
                LIMIT 5
                """, (rs, rowNum) -> new AdminOverview.FavoriteSlice(
                rs.getString("doc_id"), rs.getString("title"), rs.getLong("cnt")));

        HealthAgg health = jdbcTemplate.queryForObject("""
                SELECT (SELECT count(*) FROM vector_store) AS vector_rows,
                       pg_database_size(current_database()) AS db_size,
                       (SELECT max(fetched_at) FROM kb_document) AS last_ingested,
                       (SELECT max(created_at) FROM kb_scan_log) AS last_scan,
                       (SELECT max(created_at) FROM kb_message) AS last_qa
                """, (rs, rowNum) -> new HealthAgg(
                rs.getLong("vector_rows"), rs.getLong("db_size"),
                rs.getObject("last_ingested", OffsetDateTime.class),
                rs.getObject("last_scan", OffsetDateTime.class),
                rs.getObject("last_qa", OffsetDateTime.class)));

        double activeRate = ratio(content.active(), content.total());
        double approvalRate = ratio(review.approved(), review.approved() + review.rejected());
        double foundRate = ratio(qa.windowFound(), qa.windowQa());
        double noAnswerRate = qa.windowQa() == 0 ? 0
                : (qa.windowQa() - qa.windowFound()) / (double) qa.windowQa();
        double coverageRate = content.chunks() == 0 ? 0 : health.vectorRows() / (double) content.chunks();
        double approvedPct = ratio(review.approved(), content.total());
        double pendingPct = ratio(review.pending(), content.total());
        double rejectedPct = ratio(review.rejected(), content.total());
        long blindspotCount = qa.windowQa() - qa.windowFound();

        AdminOverview.ContentStats contentStats = new AdminOverview.ContentStats(
                content.total(), content.active(), content.inactive(),
                content.chunks(), content.avgChunks(), content.emptyChunks(),
                products.top(), products.other(),
                origins.top(), origins.other(),
                List.copyOf(groups.getOrDefault("doc_type", List.of())),
                List.copyOf(groups.getOrDefault("language", List.of())),
                versions.top(), versions.other(),
                new AdminOverview.VisibilityStats(content.teamDocs(), content.privateDocs()),
                new AdminOverview.FreshnessStats(content.fresh30(), content.fresh180(),
                        content.fresh365(), content.freshOld(), content.freshUnknown()),
                content.avgAgeDays(), content.todayNew(), content.windowNew(), content.prevWindowNew());

        AdminOverview.GovernanceStats governanceStats = new AdminOverview.GovernanceStats(
                List.of(new AdminOverview.ReviewStatusSlice("approved", review.approved(), approvedPct),
                        new AdminOverview.ReviewStatusSlice("pending", review.pending(), pendingPct),
                        new AdminOverview.ReviewStatusSlice("rejected", review.rejected(), rejectedPct)),
                approvalRate, review.avgReviewHours(), review.pendingOldestDays(), review.rejectedStale(),
                new AdminOverview.ScanStats(scan.blocks(), scan.blockedTitles(), scan.prevBlocks(), scan.hits(),
                        rules, actions),
                scan.unread());

        AdminOverview.QaStats qaStats = new AdminOverview.QaStats(
                qa.conversations(), qa.messages(), qa.windowQa(), qa.prevQa(),
                foundRate, noAnswerRate, qa.avgSources(), qa.activeAskers(), blindspots);

        AdminOverview.UserStats userStats = new AdminOverview.UserStats(
                totalUsers, admins, members, newUsers, prevNewUsers,
                activeUsers == null ? 0 : activeUsers,
                prevActiveUsers == null ? 0 : prevActiveUsers, contributors, roles);

        AdminOverview.EngagementStats engagementStats = new AdminOverview.EngagementStats(
                engagement.likes(), engagement.favorites(), topFavorites);

        AdminOverview.HealthStats healthStats = new AdminOverview.HealthStats(
                health.vectorRows(), coverageRate, health.dbSize(),
                iso(health.lastIngested()), iso(health.lastScan()), iso(health.lastQa()));

        AdminOverview.Cards cards = new AdminOverview.Cards(
                content.total(), content.chunks(), activeRate, review.pending(),
                qa.windowQa(), foundRate, totalUsers, content.windowNew());

        AdminOverview.TodoSummary todos = new AdminOverview.TodoSummary(
                review.pending(), review.rejectedStale(), scan.blocks(), blindspotCount);

        return new AdminOverview(cards,
                new AdminOverview.Sections(contentStats, governanceStats, qaStats, userStats,
                        engagementStats, healthStats),
                trend, todos,
                new AdminOverview.Meta(windowDays, OffsetDateTime.now().withNano(0).toString()));
    }

    /** 五个维度（product / origin / doc_type / language / version）的分组计数，SQL 已按计数降序。 */
    private Map<String, List<AdminOverview.ProductSlice>> loadGroupSlices() {
        Map<String, List<AdminOverview.ProductSlice>> groups = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT dim, label, cnt FROM (
                    SELECT 'product' AS dim, product AS label, count(*) AS cnt FROM kb_document GROUP BY product
                    UNION ALL
                    SELECT 'origin', origin, count(*) FROM kb_document GROUP BY origin
                    UNION ALL
                    SELECT 'doc_type', doc_type, count(*) FROM kb_document GROUP BY doc_type
                    UNION ALL
                    SELECT 'language', language, count(*) FROM kb_document GROUP BY language
                    UNION ALL
                    SELECT 'version', version, count(*) FROM kb_document GROUP BY version
                ) g
                ORDER BY dim, cnt DESC, label
                """, (RowCallbackHandler) rs -> groups
                .computeIfAbsent(rs.getString("dim"), k -> new ArrayList<>())
                .add(new AdminOverview.ProductSlice(rs.getString("label"), rs.getLong("cnt"))));
        return groups;
    }

    /** 前 8 项明细 + 其余项数与文档数之和。 */
    private static TopSlice topSlice(List<AdminOverview.ProductSlice> rows) {
        if (rows.size() <= 8) {
            return new TopSlice(List.copyOf(rows), new AdminOverview.OtherBucket(0, 0));
        }
        List<AdminOverview.ProductSlice> top = List.copyOf(rows.subList(0, 8));
        List<AdminOverview.ProductSlice> rest = rows.subList(8, rows.size());
        long total = rest.stream().mapToLong(AdminOverview.ProductSlice::count).sum();
        return new TopSlice(top, new AdminOverview.OtherBucket(rest.size(), total));
    }

    /** 近 N 天趋势：按 date_trunc('day') 聚合两张表，Java 侧用 LocalDate 补齐缺失日期为 0。 */
    private AdminOverview.TrendSeries loadTrend(int days) {
        LocalDate today = jdbcTemplate.queryForObject("SELECT current_date", LocalDate.class);
        Map<LocalDate, Long> docsByDay = new HashMap<>();
        Map<LocalDate, Long> qaByDay = new HashMap<>();
        jdbcTemplate.query("""
                SELECT date_trunc('day', created_at)::date AS d, count(*) AS cnt
                FROM kb_document
                WHERE created_at >= current_date - CAST(? AS int) * interval '1 day'
                GROUP BY date_trunc('day', created_at)::date
                """, (RowCallbackHandler) rs -> docsByDay.put(rs.getDate("d").toLocalDate(), rs.getLong("cnt")),
                days - 1);
        jdbcTemplate.query("""
                SELECT date_trunc('day', created_at)::date AS d, count(*) AS cnt
                FROM kb_message
                WHERE created_at >= current_date - CAST(? AS int) * interval '1 day'
                GROUP BY date_trunc('day', created_at)::date
                """, (RowCallbackHandler) rs -> qaByDay.put(rs.getDate("d").toLocalDate(), rs.getLong("cnt")),
                days - 1);
        List<AdminOverview.TrendPoint> points = new ArrayList<>(days);
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today == null ? LocalDate.now().minusDays(i) : today.minusDays(i);
            points.add(new AdminOverview.TrendPoint(date.toString(),
                    docsByDay.getOrDefault(date, 0L), qaByDay.getOrDefault(date, 0L)));
        }
        return new AdminOverview.TrendSeries(days, points);
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0 : numerator / (double) denominator;
    }

    private static String iso(OffsetDateTime time) {
        return time == null ? null : time.withNano(0).toString();
    }

    /** 构造 WHERE 子句：参数均以占位符绑定（无 SQL 注入面）；keyword 走 ILIKE 并转义 %/_。 */
    private Where buildWhere(String product, String source, String version, String active, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT id, title, authority, product, visibility, author, review_status, review_note, version, active, chunk_count, created_at, updated_at FROM kb_document WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (product != null && !product.isBlank()) {
            sql.append(" AND product = ?");
            args.add(product);
        }
        if (source != null && !source.isBlank()) {
            sql.append(" AND authority = ?");
            args.add(source);
        }
        if (version != null && !version.isBlank()) {
            sql.append(" AND version = ?");
            args.add(version);
        }
        if (active != null && !active.isBlank()) {
            if (!"true".equals(active) && !"false".equals(active)) {
                throw new IllegalArgumentException("active 参数仅支持 true / false");
            }
            sql.append(" AND active = ?");
            args.add(Boolean.parseBoolean(active));
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND title ILIKE ? ESCAPE '\\'");
            args.add("%" + escapeLike(keyword) + "%");
        }
        sql.append(" ORDER BY updated_at DESC");
        return new Where(sql.toString(), args);
    }

    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private record Where(String sql, List<Object> args) {

        /** 仅 WHERE 片段（不含 SELECT 与 ORDER BY），供 count 查询复用。 */
        String whereOnly() {
            int idx = sql.indexOf(" WHERE");
            return sql.substring(idx, sql.indexOf(" ORDER BY"));
        }
    }

    private static final RowMapper<DocumentDto> DOCUMENT_ROW_MAPPER = (rs, rowNum) -> new DocumentDto(
            rs.getString("id"),
            rs.getString("title"),
            rs.getString("authority"),
            rs.getString("product"),
            rs.getString("version"),
            rs.getString("visibility"),
            rs.getString("author"),
            rs.getString("review_status"),
            rs.getString("review_note"),
            rs.getBoolean("active"),
            rs.getInt("chunk_count"),
            toIso(rs.getTimestamp("created_at")),
            toIso(rs.getTimestamp("updated_at")));

    private static String toIso(Timestamp ts) {
        if (ts == null) {
            return "";
        }
        LocalDateTime ldt = ts.toLocalDateTime();
        return ldt.toLocalDate() + " " + ldt.toLocalTime().withNano(0);
    }

    // ---------- 2. 导入：单文档（表单）/ 批量（data/raw） ----------

    /** 单文档导入：校验 → 构 RawDocument → 走 IngestionService 幂等导入（同事务）。 */
    @Transactional
    public AdminImportResult importOne(ImportRequest req) {
        validate(req);
        RawDocument doc = toRawDocument(req);
        ImportResult r = ingestionService.importDocument(doc);
        if (r.status() == ImportResult.Status.IMPORTED) {
            log.info("管理端导入成功 docId={} chunkCount={}", r.docId(), r.chunkCount());
            return AdminImportResult.imported(r.docId(), r.chunkCount());
        }
        log.info("管理端导入幂等跳过 docId={}", r.docId());
        return AdminImportResult.skipped(r.docId());
    }

    /** 批量导入：从 data/raw/{vue,springai,springboot}.json 读取并导入，逐源独立事务，单源失败不中断。 */
    public Map<String, Object> importFromDisk() {
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        Map<String, Object> perSource = new LinkedHashMap<>();
        for (String source : DISK_SOURCES) {
            Path file = Path.of("data", "raw").resolve(source + ".json");
            if (!Files.exists(file)) {
                perSource.put(source, "文件不存在: " + file);
                continue;
            }
            try {
                List<RawDocument> docs = jsonMapper.readValue(Files.readString(file), RAW_DOC_LIST);
                ImportSummary summary = ingestionService.importAll(docs);
                imported += summary.imported();
                skipped += summary.skipped();
                failed += summary.failed();
                perSource.put(source, Map.of(
                        "imported", summary.imported(),
                        "skipped", summary.skipped(),
                        "failed", summary.failed()));
            } catch (Exception e) {
                failed++;
                log.error("管理端批量导入失败 source={}", source, e);
                perSource.put(source, "解析失败: " + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("skipped", skipped);
        result.put("failed", failed);
        result.put("sources", perSource);
        return result;
    }

    private void validate(ImportRequest req) {
        if (req.source() == null || !(req.source().equals("official") || req.source().equals("community"))) {
            throw new IllegalArgumentException("source 仅支持 official / community");
        }
        if (isBlank(req.version()) || isBlank(req.title()) || isBlank(req.docKey())) {
            throw new IllegalArgumentException("version / title / docKey 均为必填");
        }
        if ("official".equals(req.source())) {
            if (isBlank(req.text()) && isBlank(req.url())) {
                throw new IllegalArgumentException("official 文档需提供 text 或 url 其中之一");
            }
            if (!isBlank(req.url()) && !isHttpUrl(req.url())) {
                throw new IllegalArgumentException("url 需以 http:// 或 https:// 开头");
            }
        } else if (isBlank(req.text())) {
            throw new IllegalArgumentException("community 文档必须提供 text 正文");
        }
    }

    /** 构造 RawDocument：官方可走 URL 抓取（域名白名单，防 SSRF），否则正文即 markdown。 */
    private RawDocument toRawDocument(ImportRequest req) {
        boolean community = "community".equals(req.source());
        String rawSource = community ? "manual-community" : "manual-official";
        String markdown;
        List<ImageRef> images = List.of();
        List<HeadingNode> headings = List.of();
        if (!community && !isBlank(req.url()) && isBlank(req.text())) {
            HtmlToMarkdown.Result fetched = fetchUrl(req.url());
            markdown = fetched.markdown();
            images = fetched.images();
            headings = fetched.headings();
        } else {
            markdown = req.text();
        }
        return new RawDocument(
                rawSource, isBlank(req.product()) ? "manual" : req.product().trim(),
                req.version(), "en", "manual", req.title(),
                req.url(), req.docKey(), null, List.of(), null,
                "", markdown, images, headings);
    }

    /** URL 抓取：白名单域名 + 15s 超时 + 桌面 UA；失败统一抛 400 语义异常。 */
    private HtmlToMarkdown.Result fetchUrl(String url) {
        String host;
        try {
            host = URI.create(url).getHost();
        } catch (Exception e) {
            throw new IllegalArgumentException("非法 URL: " + url);
        }
        if (host == null || ALLOWED_HOST_SUFFIXES.stream().noneMatch(s -> host.equals(s) || host.endsWith("." + s))) {
            throw new IllegalArgumentException("仅允许抓取官方文档站（vuejs.org / spring.io），已拒绝: " + host);
        }
        try {
            Document html = Jsoup.connect(url)
                    .timeout(15_000)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) SpringKB-RAG/1.0")
                    .followRedirects(true)
                    .get();
            Element root = html.selectFirst("main") != null ? html.selectFirst("main") : html.body();
            return HtmlToMarkdown.convert(root, url);
        } catch (IOException e) {
            throw new IllegalArgumentException("URL 抓取失败: " + e.getMessage());
        }
    }

    // ---------- 3. 版本状态切换：kb_document + vector_store metadata 双写 ----------

    /** active 双写：文档表布尔字段 + 向量库 metadata JSON 'active' 字符串，保证检索过滤一致。 */
    @Transactional
    public DocumentDto setActive(String docId, Boolean active) {
        if (active == null) {
            throw new IllegalArgumentException("active 必填（true / false）");
        }
        int n = jdbcTemplate.update("UPDATE kb_document SET active = ?, updated_at = now() WHERE id = ?",
                active, docId);
        if (n == 0) {
            throw new NotFoundException("文档不存在: " + docId);
        }
        String jsonActive = active ? "\"true\"" : "\"false\"";
        jdbcTemplate.update(
                "UPDATE vector_store SET metadata = jsonb_set(metadata::jsonb, '{active}', ?::jsonb) WHERE metadata ->> 'doc_id' = ?",
                jsonActive, docId);
        return findById(docId);
    }

    // ---------- 4. 删除文档（含向量与 chunk） ----------

    @Transactional
    public void delete(String docId) {
        jdbcTemplate.update("DELETE FROM vector_store WHERE metadata ->> 'doc_id' = ?", docId);
        int n = jdbcTemplate.update("DELETE FROM kb_document WHERE id = ?", docId);
        if (n == 0) {
            throw new NotFoundException("文档不存在: " + docId);
        }
        log.info("管理端删除文档 docId={}", docId);
    }

    // ---------- 5. chunk 列表（引用溯源调试） ----------

    public List<Map<String, Object>> listChunks(String docId) {
        if (!existsDoc(docId)) {
            throw new NotFoundException("文档不存在: " + docId);
        }
        return jdbcTemplate.query("""
                SELECT c.id, c.ordinal, c.heading_path, c.content_markdown, c.token_count, c.image_refs::text AS image_refs,
                       d.authority, d.version, d.section
                FROM kb_chunk c JOIN kb_document d ON d.id = c.doc_id
                WHERE c.doc_id = ?
                ORDER BY c.ordinal
                """, (rs, rowNum) -> {
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("heading_path", rs.getString("heading_path"));
                    metadata.put("source", rs.getString("authority"));
                    metadata.put("version", rs.getString("version"));
                    metadata.put("section", rs.getString("section") == null ? "" : rs.getString("section"));
                    metadata.put("token_count", rs.getInt("token_count"));
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("content", rs.getString("content_markdown"));
                    row.put("metadata", metadata);
                    return row;
                }, docId);
    }

    // ---------- 6. 元数据编辑：技术分类 / 版本 / 可见性 ----------

    /**
     * 编辑文档元数据并同步向量 metadata（product/version/visibility 参与检索过滤与原文外链重建）。
     *
     * <p>审核联动：管理员把私有文档改为团队可见时同样要过脱敏扫描并进入待审，避免
     * 「先建私有、再改团队」绕过审核；改为私有则跳过审核并立即生效。</p>
     */
    @Transactional
    public DocumentDto updateMeta(String docId, MetaUpdateRequest req, String operator) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        DocumentDto current = findById(docId);
        String product = isBlank(req.product()) ? current.product() : req.product().trim();
        String version = isBlank(req.version()) ? current.version() : req.version().trim();
        String visibility = isBlank(req.visibility()) ? current.visibility() : req.visibility().trim().toLowerCase();
        if (!"team".equals(visibility) && !"private".equals(visibility)) {
            throw new IllegalArgumentException("visibility 仅支持 team / private");
        }
        boolean becomingTeam = "team".equals(visibility) && !"team".equals(current.visibility());
        String reviewStatus = current.reviewStatus();
        boolean clearTrace = false;
        if ("private".equals(visibility)) {
            reviewStatus = UgcService.REVIEW_APPROVED;
            clearTrace = true;
        } else if (becomingTeam) {
            SensitiveScanService.Result scan = scanRuleService.scan(current.title() + "\n" + contentOf(docId));
            if (scan.blocked()) {
                scanAuditService.record(operator, "admin_meta", current.title(), scan);
                throw new SensitiveScanException(scan);
            }
            reviewStatus = UgcService.REVIEW_PENDING;
            clearTrace = true;
        }
        if (clearTrace) {
            jdbcTemplate.update("""
                    UPDATE kb_document
                    SET product = ?, version = ?, visibility = ?, review_status = ?,
                        review_note = NULL, reviewed_by = NULL, reviewed_at = NULL, updated_at = now()
                    WHERE id = ?
                    """, product, version, visibility, reviewStatus, docId);
        } else {
            jdbcTemplate.update(
                    "UPDATE kb_document SET product = ?, version = ?, visibility = ?, updated_at = now() WHERE id = ?",
                    product, version, visibility, docId);
        }
        jdbcTemplate.update("""
                UPDATE vector_store SET metadata = jsonb_set(
                    jsonb_set(jsonb_set(jsonb_set(metadata::jsonb, '{product}', to_jsonb(?::text)),
                              '{version}', to_jsonb(?::text)),
                              '{visibility}', to_jsonb(?::text)),
                    '{review_status}', to_jsonb(?::text))
                WHERE metadata ->> 'doc_id' = ?
                """, product, version, visibility, reviewStatus, docId);
        registerCategory(product, product);
        return findById(docId);
    }

    /** 拼接文档正文（用于可见性切换时的脱敏扫描）。 */
    private String contentOf(String docId) {
        return jdbcTemplate.query(
                        "SELECT content_markdown FROM kb_chunk WHERE doc_id = ? ORDER BY ordinal",
                        (rs, rowNum) -> rs.getString("content_markdown"), docId)
                .stream()
                .filter(s -> s != null && !s.isBlank())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }

    // ---------- 7. 技术分类与版本 ----------

    /** 管理端技术分类：注册表 ∪ 文档 product，版本由文档动态聚合（含 private 与未激活文档）。 */
    public List<CategoryDto> categories() {
        Map<String, String> names = new LinkedHashMap<>();
        // 必须用块体 lambda：表达式体 `rs -> map.put(...)` 同时兼容
        // ResultSetExtractor<String>（有返回值）与 RowCallbackHandler（void），会产生重载歧义
        jdbcTemplate.query("SELECT product, display_name FROM kb_category ORDER BY product", rs -> {
            names.put(rs.getString("product"), rs.getString("display_name"));
        });
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, List<String>> versions = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT product, version, count(*) AS cnt FROM kb_document
                GROUP BY product, version ORDER BY product, version
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            String product = rs.getString("product");
            String version = rs.getString("version");
            if (version == null || version.isBlank()) {
                version = UgcService.UNVERSIONED;
            }
            counts.merge(product, rs.getLong("cnt"), Long::sum);
            List<String> list = versions.computeIfAbsent(product, k -> new ArrayList<>());
            if (!list.contains(version)) {
                list.add(version);
            }
        });
        List<CategoryDto> result = new ArrayList<>();
        for (var entry : names.entrySet()) {
            result.add(new CategoryDto(entry.getKey(), entry.getValue(),
                    counts.getOrDefault(entry.getKey(), 0L), versions.getOrDefault(entry.getKey(), List.of())));
        }
        for (var entry : counts.entrySet()) {
            if (!names.containsKey(entry.getKey())) {
                result.add(new CategoryDto(entry.getKey(), entry.getKey(), entry.getValue(),
                        versions.getOrDefault(entry.getKey(), List.of())));
            }
        }
        return result;
    }

    /** 新增 / 重命名技术分类（product 为主键，重复时更新显示名）。 */
    public void addCategory(CategoryCreateRequest req) {
        if (req == null || isBlank(req.product())) {
            throw new IllegalArgumentException("技术分类标识不能为空");
        }
        String product = req.product().trim();
        String displayName = isBlank(req.displayName()) ? product : req.displayName().trim();
        registerCategory(product, displayName);
    }

    private void registerCategory(String product, String displayName) {
        jdbcTemplate.update("""
                INSERT INTO kb_category (product, display_name) VALUES (?, ?)
                ON CONFLICT (product) DO UPDATE SET display_name = EXCLUDED.display_name
                """, product, displayName);
    }

    // ---------- 8. 成员与角色管理 ----------

    public List<MemberDto> members() {
        return userRepository.listMembers();
    }

    /**
     * 修改成员角色：只允许 ADMIN / MEMBER；禁止修改自己的角色（避免误操作把自己锁在门外），
     * 且至少保留一名管理员（禁止降级最后一名 ADMIN）。
     */
    @Transactional
    public MemberDto updateRole(long id, String role, String currentUsername) {
        if (!AuthService.ROLE_ADMIN.equals(role) && !AuthService.ROLE_MEMBER.equals(role)) {
            throw new IllegalArgumentException("role 仅支持 ADMIN / MEMBER");
        }
        MemberDto target = userRepository.listMembers().stream()
                .filter(m -> m.id() == id)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("成员不存在: " + id));
        if (target.username().equals(currentUsername)) {
            throw new IllegalArgumentException("不能修改自己的角色");
        }
        if (AuthService.ROLE_ADMIN.equals(target.role()) && AuthService.ROLE_MEMBER.equals(role)
                && userRepository.countByRole(AuthService.ROLE_ADMIN) <= 1) {
            throw new IllegalArgumentException("系统至少需要保留一名管理员");
        }
        userRepository.updateRole(id, role);
        return new MemberDto(target.id(), target.username(), target.email(), role, target.createdAt());
    }

    // ---------- helpers（概览聚合的中间结果） ----------

    private record ContentAgg(long total, long active, long inactive, long chunks, double avgChunks,
                              long emptyChunks, long teamDocs, long privateDocs, long fresh30,
                              long fresh180, long fresh365, long freshOld, long freshUnknown,
                              double avgAgeDays, long todayNew, long windowNew, long prevWindowNew) {
    }

    private record GovernanceAgg(long approved, long pending, long rejected, double avgReviewHours,
                                 double pendingOldestDays, long rejectedStale) {
    }

    private record ScanAgg(long unread, long blocks, long blockedTitles, long prevBlocks, long hits) {
    }

    private record QaAgg(long conversations, long messages, long windowQa, long prevQa,
                         long windowFound, double avgSources, long activeAskers) {
    }

    private record UserRoleAgg(String role, long count, long newCount, long prevNewCount) {
    }

    private record EngagementAgg(long likes, long favorites) {
    }

    private record HealthAgg(long vectorRows, long dbSize, OffsetDateTime lastIngested,
                             OffsetDateTime lastScan, OffsetDateTime lastQa) {
    }

    private record TopSlice(List<AdminOverview.ProductSlice> top, AdminOverview.OtherBucket other) {
    }

    // ---------- helpers ----------

    private DocumentDto findById(String docId) {
        return jdbcTemplate.query("""
                SELECT id, title, authority, product, visibility, author, review_status, review_note,
                       version, active, chunk_count, created_at, updated_at
                FROM kb_document WHERE id = ?
                """, DOCUMENT_ROW_MAPPER, docId).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("文档不存在: " + docId));
    }

    private boolean existsDoc(String docId) {
        Integer n = jdbcTemplate.queryForObject("SELECT count(*) FROM kb_document WHERE id = ?", Integer.class, docId);
        return n != null && n > 0;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean isHttpUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }
}
