package com.guangqian.springkb.manage;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.guangqian.springkb.common.NotFoundException;
import com.guangqian.springkb.crawler.HeadingNode;
import com.guangqian.springkb.crawler.HtmlToMarkdown;
import com.guangqian.springkb.crawler.ImageRef;
import com.guangqian.springkb.crawler.RawDocument;
import com.guangqian.springkb.dto.AdminImportResult;
import com.guangqian.springkb.dto.DocumentDto;
import com.guangqian.springkb.dto.ImportRequest;
import com.guangqian.springkb.dto.PageResult;
import com.guangqian.springkb.ingestion.IngestionService;
import com.guangqian.springkb.ingestion.ImportResult;
import com.guangqian.springkb.ingestion.ImportSummary;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
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

    public AdminService(JdbcTemplate jdbcTemplate, IngestionService ingestionService, JsonMapper jsonMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.ingestionService = ingestionService;
        this.jsonMapper = jsonMapper;
    }

    // ---------- 1. 文档列表：过滤（source/version/active/keyword）+ 可选分页 ----------

    /** 全量列表（不分页，默认形态，前端列表/仪表盘直接消费）。 */
    public List<DocumentDto> listAll(String source, String version, String active, String keyword) {
        Where w = buildWhere(source, version, active, keyword);
        return jdbcTemplate.query(w.sql(), DOCUMENT_ROW_MAPPER, w.args().toArray());
    }

    /** 分页列表：page 从 1 起、size 钳制 1~200；与全量列表共用同一 WHERE。 */
    public PageResult<DocumentDto> page(String source, String version, String active, String keyword,
            int page, int size) {
        Where w = buildWhere(source, version, active, keyword);
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

    /** 构造 WHERE 子句：参数均以占位符绑定（无 SQL 注入面）；keyword 走 ILIKE 并转义 %/_。 */
    private Where buildWhere(String source, String version, String active, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT id, title, authority, version, active, chunk_count, created_at, updated_at FROM kb_document WHERE 1=1");
        List<Object> args = new ArrayList<>();
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
            rs.getString("version"),
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
                rawSource, "manual", req.version(), "en", "manual", req.title(),
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

    // ---------- helpers ----------

    private DocumentDto findById(String docId) {
        return jdbcTemplate.query("""
                SELECT id, title, authority, version, active, chunk_count, created_at, updated_at
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
