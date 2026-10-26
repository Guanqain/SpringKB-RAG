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

    // ---------- 1. 文档列表：过滤（product/source/version/active/keyword）+ 可选分页 ----------

    /** 全量列表（不分页，默认形态，前端列表/仪表盘直接消费）。 */
    public List<DocumentDto> listAll(String product, String source, String version, String active, String keyword) {
        Where w = buildWhere(product, source, version, active, keyword);
        return jdbcTemplate.query(w.sql(), DOCUMENT_ROW_MAPPER, w.args().toArray());
    }

    /** 分页列表：page 从 1 起、size 钳制 1~200；与全量列表共用同一 WHERE。 */
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
