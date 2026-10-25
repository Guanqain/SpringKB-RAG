package com.guangqian.springkb.kb;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.guangqian.springkb.common.NotFoundException;
import com.guangqian.springkb.common.OriginalUrlResolver;
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
 * 只展示 active 文档；where 参数均以占位符绑定，无 SQL 注入面。
 */
@Service
public class KbService {

    private static final String META_SELECT = """
            SELECT id, title, product, origin, authority, version, doc_key, section, doc_type, language,
                   chunk_count, last_modified, updated_at, author
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
                rs.getString("author"));
    };

    private final JdbcTemplate jdbcTemplate;

    public KbService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public KbOverview overview() {
        return new KbOverview(
                count("SELECT count(*) FROM kb_document WHERE active = true"),
                count("SELECT count(DISTINCT product) FROM kb_document WHERE active = true"),
                count("SELECT count(DISTINCT version) FROM kb_document WHERE active = true"),
                count("SELECT count(DISTINCT authority) FROM kb_document WHERE active = true"));
    }

    public List<KbTreeNode> tree() {
        List<Object[]> rows = jdbcTemplate.query("""
                SELECT product, version, authority, count(*) AS cnt
                FROM kb_document WHERE active = true
                GROUP BY product, version, authority
                ORDER BY product, version, authority
                """, (rs, rowNum) -> new Object[]{
                    rs.getString("product"), rs.getString("version"),
                    rs.getString("authority"), rs.getInt("cnt")});
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
    public List<KbDocCard> docs(String product, String version, String source, String keyword) {
        Where w = docsWhere(product, version, source, keyword);
        return jdbcTemplate.query(w.sql(), CARD_MAPPER, w.args().toArray());
    }

    /** 分页列表：page 从 1 起、size 钳制 1~100；与全量列表共用同一 WHERE。 */
    public PageResult<KbDocCard> docsPage(String product, String version, String source, String keyword,
            int page, int size) {
        Where w = docsWhere(product, version, source, keyword);
        long total = count(w.whereOnly(), w.args().toArray());
        int p = Math.max(1, page);
        int s = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        List<Object> args = new ArrayList<>(w.args());
        args.add(s);
        args.add((long) (p - 1) * s);
        List<KbDocCard> list = jdbcTemplate.query(w.sql() + " LIMIT ? OFFSET ?", CARD_MAPPER, args.toArray());
        return new PageResult<>(list, total, p, s);
    }

    private Where docsWhere(String product, String version, String source, String keyword) {
        StringBuilder sql = new StringBuilder(META_SELECT).append(" WHERE active = true");
        List<Object> args = new ArrayList<>();
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

    /** 详情（匿名访问）：点赞/收藏状态为 false。 */
    public KbDocDetail doc(String id) {
        return doc(id, null);
    }

    /** 详情：附加点赞/收藏公开计数与当前用户状态。 */
    public KbDocDetail doc(String id, String username) {
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
        List<KbDocDetail> found = jdbcTemplate.query(META_SELECT + " WHERE id = ?", (rs, rowNum) -> {
            String v = rs.getString("version");
            return new KbDocDetail(
                    rs.getString("id"), rs.getString("title"), rs.getString("product"),
                    rs.getString("origin"), rs.getString("authority"), v, rs.getString("doc_key"),
                    rs.getString("section"), rs.getString("doc_type"), rs.getString("language"),
                    rs.getInt("chunk_count"), toIso(rs.getTimestamp("last_modified")),
                    toIso(rs.getTimestamp("updated_at")),
                    OriginalUrlResolver.resolve(rs.getString("product"), rs.getString("origin"), v, rs.getString("doc_key")),
                    markdown, rs.getString("author"), likeCount, favoriteCount, liked, favorited);
        }, id);
        if (found.isEmpty()) {
            throw new NotFoundException("文档不存在: " + id);
        }
        return found.get(0);
    }

    /** 当前用户写过的文档（UGC 归属）。 */
    public List<KbDocCard> myDocs(String username) {
        return jdbcTemplate.query(META_SELECT + " WHERE active = true AND author = ? ORDER BY updated_at DESC",
                CARD_MAPPER, username);
    }

    /** 当前用户收藏的文档（按收藏时间倒序）。 */
    public List<KbDocCard> favoriteDocs(String username) {
        return jdbcTemplate.query("""
                SELECT d.id, d.title, d.product, d.origin, d.authority, d.version, d.doc_key, d.section,
                       d.doc_type, d.language, d.chunk_count, d.last_modified, d.updated_at, d.author
                FROM kb_document d JOIN kb_doc_favorite f ON f.doc_id = d.id
                WHERE d.active = true AND f.username = ?
                ORDER BY f.created_at DESC
                """, CARD_MAPPER, username);
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
