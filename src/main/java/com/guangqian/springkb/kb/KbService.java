package com.guangqian.springkb.kb;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.guangqian.springkb.common.NotFoundException;
import com.guangqian.springkb.common.OriginalUrlResolver;
import com.guangqian.springkb.dto.CategoryDto;
import com.guangqian.springkb.dto.KbDocCard;
import com.guangqian.springkb.dto.KbDocDetail;
import com.guangqian.springkb.dto.KbOverview;
import com.guangqian.springkb.dto.KbTreeNode;
import com.guangqian.springkb.dto.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * 知识库导航（用户端只读）：概览统计、体系树（产品线 ▸ 版本 ▸ 来源）、文档卡列表（分页）、
 * 文档详情（Markdown 全文 + 点赞收藏计数）、个人文档与收藏列表。
 *
 * <p>可见性：所有面向「他人」的查询统一附加
 * {@code visibility='team' OR (visibility='private' AND author=当前用户)}；
 * 匿名用户只能看到 team 文档。private 文档对非作者按 404 处理（不泄漏存在性）。</p>
 *
 * <p>只展示 active 文档；where 参数均以占位符绑定，无 SQL 注入面。</p>
 */
@Service
public class KbService {

    private static final String META_SELECT = """
            SELECT id, title, product, origin, authority, version, doc_key, section, doc_type, language,
                   chunk_count, last_modified, updated_at, author, visibility, review_status, review_note
            FROM kb_document
            """;

    private static final int MAX_PAGE_SIZE = 100;

    private static final RowMapper<KbDocCard> CARD_MAPPER = (rs, rowNum) -> {
        String v = rs.getString("version");
        return new KbDocCard(
                rs.getString("id"), rs.getString("title"), rs.getString("product"),
                rs.getString("origin"), rs.getString("authority"), v, rs.getString("section"),
                rs.getString("doc_type"), rs.getInt("chunk_count"), toIso(rs.getTimestamp("updated_at")),
                OriginalUrlResolver.resolve(rs.getString("product"), rs.getString("origin"), v, rs.getString("doc_key")),
                rs.getString("author"), rs.getString("visibility"),
                rs.getString("review_status"), rs.getString("review_note"));
    };

    private final JdbcTemplate jdbcTemplate;

    public KbService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public KbOverview overview(String username) {
        StringBuilder where = new StringBuilder(" WHERE active = true");
        List<Object> args = new ArrayList<>();
        appendVisibility(where, args, username);
        return new KbOverview(
                count("SELECT count(*) FROM kb_document" + where, args.toArray()),
                count("SELECT count(DISTINCT product) FROM kb_document" + where, args.toArray()),
                count("SELECT count(DISTINCT version) FROM kb_document" + where, args.toArray()),
                count("SELECT count(DISTINCT authority) FROM kb_document" + where, args.toArray()));
    }

    public List<KbTreeNode> tree(String username) {
        StringBuilder sql = new StringBuilder("""
                SELECT product, version, authority, count(*) AS cnt
                FROM kb_document WHERE active = true
                """);
        List<Object> args = new ArrayList<>();
        appendVisibility(sql, args, username);
        sql.append(" GROUP BY product, version, authority ORDER BY product, version, authority");
        List<Object[]> rows = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new Object[]{
                rs.getString("product"), rs.getString("version"),
                rs.getString("authority"), rs.getInt("cnt")}, args.toArray());
        // product ▸ version ▸ source 三级聚合，保持字典序
        LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, Integer>>> agg = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String product = (String) row[0];
            String version = (String) row[1];
            String source = (String) row[2];
            int cnt = (Integer) row[3];
            agg.computeIfAbsent(product, k -> new LinkedHashMap<>())
                    .computeIfAbsent(version, k -> new LinkedHashMap<>())
                    .merge(source, cnt, Integer::sum);
        }
        List<KbTreeNode> roots = new ArrayList<>();
        for (var p : agg.entrySet()) {
            List<KbTreeNode> versionNodes = new ArrayList<>();
            int productTotal = 0;
            for (var v : p.getValue().entrySet()) {
                List<KbTreeNode> sourceNodes = new ArrayList<>();
                int versionTotal = 0;
                for (var s : v.getValue().entrySet()) {
                    sourceNodes.add(new KbTreeNode(s.getKey(), s.getKey(), "source", s.getValue(), List.of()));
                    versionTotal += s.getValue();
                }
                versionNodes.add(new KbTreeNode(v.getKey(), v.getKey(), "version", versionTotal, sourceNodes));
                productTotal += versionTotal;
            }
            roots.add(new KbTreeNode(p.getKey(), p.getKey(), "product", productTotal, versionNodes));
        }
        return roots;
    }

    /** 全量列表（兼容旧调用；前端使用 {@link #docsPage} 分页）。 */
    public List<KbDocCard> docs(String product, String version, String source, String keyword, String username) {
        Where w = docsWhere(product, version, source, keyword, username);
        return jdbcTemplate.query(w.sql(), CARD_MAPPER, w.args().toArray());
    }

    /** 分页列表：page 从 1 起、size 钳制 1~100；与全量列表共用同一 WHERE。 */
    public PageResult<KbDocCard> docsPage(String product, String version, String source, String keyword,
            int page, int size, String username) {
        Where w = docsWhere(product, version, source, keyword, username);
        long total = count(w.whereOnly(), w.args().toArray());
        int p = Math.max(1, page);
        int s = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        List<Object> args = new ArrayList<>(w.args());
        args.add(s);
        args.add((long) (p - 1) * s);
        List<KbDocCard> list = jdbcTemplate.query(w.sql() + " LIMIT ? OFFSET ?", CARD_MAPPER, args.toArray());
        return new PageResult<>(list, total, p, s);
    }

    private Where docsWhere(String product, String version, String source, String keyword, String username) {
        StringBuilder sql = new StringBuilder(META_SELECT).append(" WHERE active = true");
        List<Object> args = new ArrayList<>();
        appendVisibility(sql, args, username);
        if (notBlank(product)) {
            sql.append(" AND product = ?");
            args.add(product);
        }
        if (notBlank(version)) {
            sql.append(" AND version = ?");
            args.add(version);
        }
        if (notBlank(source)) {
            sql.append(" AND authority = ?");
            args.add(source);
        }
        if (notBlank(keyword)) {
            sql.append(" AND title ILIKE ? ESCAPE '\\'");
            args.add("%" + escapeLike(keyword) + "%");
        }
        sql.append(" ORDER BY updated_at DESC");
        return new Where(sql.toString(), args);
    }

    /** 详情（匿名访问）：点赞/收藏状态为 false，仅可读 team 文档。 */
    public KbDocDetail doc(String id) {
        return doc(id, null);
    }

    /** 详情：附加点赞/收藏公开计数与当前用户状态；private 文档仅作者可读，否则 404。 */
    public KbDocDetail doc(String id, String username) {
        StringBuilder where = new StringBuilder(" WHERE id = ?");
        List<Object> args = new ArrayList<>();
        args.add(id);
        appendVisibility(where, args, username);
        String markdown = jdbcTemplate.query(
                "SELECT content_markdown FROM kb_chunk WHERE doc_id = ? ORDER BY ordinal",
                (rs, rowNum) -> rs.getString("content_markdown"), id)
                .stream()
                .filter(s -> s != null && !s.isBlank())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
        long likeCount = count("SELECT count(*) FROM kb_doc_like WHERE doc_id = ?", id);
        long favoriteCount = count("SELECT count(*) FROM kb_doc_favorite WHERE doc_id = ?", id);
        boolean liked = username != null && exists("SELECT count(*) FROM kb_doc_like WHERE doc_id = ? AND username = ?", id, username);
        boolean favorited = username != null && exists("SELECT count(*) FROM kb_doc_favorite WHERE doc_id = ? AND username = ?", id, username);
        List<KbDocDetail> found = jdbcTemplate.query(META_SELECT + where, (rs, rowNum) -> {
            String v = rs.getString("version");
            return new KbDocDetail(
                    rs.getString("id"), rs.getString("title"), rs.getString("product"),
                    rs.getString("origin"), rs.getString("authority"), v, rs.getString("doc_key"),
                    rs.getString("section"), rs.getString("doc_type"), rs.getString("language"),
                    rs.getInt("chunk_count"), toIso(rs.getTimestamp("last_modified")),
                    toIso(rs.getTimestamp("updated_at")),
                    OriginalUrlResolver.resolve(rs.getString("product"), rs.getString("origin"), v, rs.getString("doc_key")),
                    markdown, rs.getString("author"), likeCount, favoriteCount, liked, favorited,
                    rs.getString("visibility"), rs.getString("review_status"), rs.getString("review_note"));
        }, args.toArray());
        if (found.isEmpty()) {
            throw new NotFoundException("文档不存在: " + id);
        }
        return found.get(0);
    }

    /** 当前用户写过的文档（UGC 归属；含自己可见的 private 文档）。 */
    public List<KbDocCard> myDocs(String username) {
        return jdbcTemplate.query(META_SELECT + " WHERE active = true AND author = ? ORDER BY updated_at DESC",
                CARD_MAPPER, username);
    }

    /** 当前用户收藏的文档（按收藏时间倒序；他人未过审/private 文档即使被收藏也不展示）。 */
    public List<KbDocCard> favoriteDocs(String username) {
        StringBuilder sql = new StringBuilder("""
                SELECT d.id, d.title, d.product, d.origin, d.authority, d.version, d.doc_key, d.section,
                       d.doc_type, d.language, d.chunk_count, d.last_modified, d.updated_at, d.author,
                       d.visibility, d.review_status, d.review_note
                FROM kb_document d JOIN kb_doc_favorite f ON f.doc_id = d.id
                WHERE d.active = true AND f.username = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(username);
        sql.append(" AND ((d.visibility = 'team' AND d.review_status = 'approved') OR d.author = ?)");
        args.add(username);
        sql.append(" ORDER BY f.created_at DESC");
        return jdbcTemplate.query(sql.toString(), CARD_MAPPER, args.toArray());
    }

    /**
     * 技术分类 + 各自版本：注册表 kb_category 与文档中出现的 product 取并集，
     * 版本由 team 可见的 active 文档动态聚合（版本空串 → 「未标注」）。
     */
    public List<CategoryDto> categories() {
        LinkedHashMap<String, String> names = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT product, display_name FROM kb_category ORDER BY product", rs -> {
            names.put(rs.getString("product"), rs.getString("display_name"));
        });
        LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
        LinkedHashMap<String, List<String>> versions = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT product, version, count(*) AS cnt
                FROM kb_document
                WHERE active = true AND visibility = 'team' AND review_status = 'approved'
                GROUP BY product, version
                ORDER BY product, version
                """, rs -> {
            String product = rs.getString("product");
            String version = rs.getString("version");
            if (version == null || version.isBlank()) {
                version = "未标注";
            }
            counts.merge(product, rs.getLong("cnt"), Long::sum);
            versions.computeIfAbsent(product, k -> new ArrayList<>()).add(version);
        });
        List<CategoryDto> result = new ArrayList<>();
        for (var entry : names.entrySet()) {
            result.add(new CategoryDto(entry.getKey(), entry.getValue(),
                    counts.getOrDefault(entry.getKey(), 0L),
                    versions.getOrDefault(entry.getKey(), List.of())));
        }
        for (var entry : counts.entrySet()) {
            if (!names.containsKey(entry.getKey())) {
                result.add(new CategoryDto(entry.getKey(), entry.getKey(), entry.getValue(),
                        versions.getOrDefault(entry.getKey(), List.of())));
            }
        }
        return result;
    }

    /**
     * 追加可见性谓词：匿名 → 仅「团队可见且审核通过」；登录用户 → 上述条件或自己写的文档
     * （作者可见自己的待审/已驳回/私有文档，非作者一律不可见）。
     */
    private static void appendVisibility(StringBuilder sql, List<Object> args, String username) {
        if (username == null || username.isBlank()) {
            sql.append(" AND visibility = 'team' AND review_status = 'approved'");
        } else {
            sql.append(" AND ((visibility = 'team' AND review_status = 'approved') OR author = ?)");
            args.add(username);
        }
    }

    private long count(String sql, Object... args) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class, args);
        return n == null ? 0L : n;
    }

    private boolean exists(String sql, Object... args) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class, args);
        return n != null && n > 0;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String toIso(Timestamp ts) {
        if (ts == null) {
            return "";
        }
        LocalDateTime ldt = ts.toLocalDateTime();
        return ldt.toLocalDate() + " " + ldt.toLocalTime().withNano(0);
    }

    private record Where(String sql, List<Object> args) {

        /** 仅 WHERE 片段（不含 SELECT 与 ORDER BY）拼上 count，供分页总数查询复用。 */
        String whereOnly() {
            int idx = sql.indexOf(" WHERE");
            return "SELECT count(*) FROM kb_document" + sql.substring(idx, sql.indexOf(" ORDER BY"));
        }
    }
}
