package com.guangqian.springkb.kb;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.guangqian.springkb.common.NotFoundException;
import com.guangqian.springkb.crawler.RawDocument;
import com.guangqian.springkb.dto.UgcPublishRequest;
import com.guangqian.springkb.ingestion.ImportResult;
import com.guangqian.springkb.ingestion.IngestionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * UGC 文档上传：把用户 Markdown / 文本文件构造成 {@link RawDocument}（source=ugc）走现有
 * {@link IngestionService} 真实入库（分块 + 本地 embedding + 写向量库 + 写 kb_document/kb_chunk）。
 *
 * <p>发布策略：</p>
 * <ul>
 *   <li>private（仅作者可见）：跳过脱敏扫描与管理员审核，入库即针对作者可见，永不进入问答检索；</li>
 *   <li>team（团队可见）：先做脱敏扫描，命中敏感信息直接拦截（不落库）；通过后以
 *       {@code review_status=pending} 入库，待管理员审核通过才进入知识库与问答引用。</li>
 * </ul>
 *
 * <p>作者可编辑自己已发布的文档：编辑会删除旧文档（含向量与 chunk）后重新入库；
 * 团队文档重新进入待审队列，私有文档仍然免审。</p>
 */
@Service
public class UgcService {

    public static final String VISIBILITY_TEAM = "team";
    public static final String VISIBILITY_PRIVATE = "private";
    public static final String UNCATEGORIZED_PRODUCT = "未分类";
    public static final String UNVERSIONED = "未标注";

    public static final String REVIEW_APPROVED = "approved";
    public static final String REVIEW_PENDING = "pending";
    public static final String REVIEW_REJECTED = "rejected";

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private final IngestionService ingestionService;
    private final JdbcTemplate jdbcTemplate;
    private final ScanRuleService scanRuleService;
    private final ScanAuditService scanAuditService;

    public UgcService(IngestionService ingestionService, JdbcTemplate jdbcTemplate,
                      ScanRuleService scanRuleService, ScanAuditService scanAuditService) {
        this.ingestionService = ingestionService;
        this.jdbcTemplate = jdbcTemplate;
        this.scanRuleService = scanRuleService;
        this.scanAuditService = scanAuditService;
    }

    /** 发布结果：docId 为新文档主键，chunkCount 为分块数，reviewStatus 为审核状态。 */
    public record UgcPublishResult(String docId, int chunkCount, String visibility, String reviewStatus,
                                   String message) {
    }

    @Transactional
    public UgcPublishResult publish(UgcPublishRequest req, String author) {
        return publish(req, author, "publish");
    }

    private UgcPublishResult publish(UgcPublishRequest req, String author, String action) {
        validate(req);
        String product = normalizeProduct(req.product());
        String version = normalizeVersion(req.version());
        String visibility = normalizeVisibility(req.visibility());
        boolean team = VISIBILITY_TEAM.equals(visibility);
        if (team) {
            requireClean(req.title(), req.markdown(), author, action);
        }
        String docKey = slug(req.title()) + "-" + UUID.randomUUID().toString().substring(0, 8);
        RawDocument doc = new RawDocument(
                "ugc", product, version, "zh", "ugc", req.title().trim(), "", docKey,
                req.section(), List.of(), null, req.markdown(), req.markdown(), List.of(), List.of());
        ImportResult r = ingestionService.importDocument(doc);
        if (r.status() == ImportResult.Status.IMPORTED) {
            // 与 importDocument 同一事务：归属/可见性/审核状态写入失败则整体回滚
            String reviewStatus = team ? REVIEW_PENDING : REVIEW_APPROVED;
            jdbcTemplate.update("""
                    UPDATE kb_document
                    SET author = ?, visibility = ?, review_status = ?, review_note = NULL,
                        reviewed_by = NULL, reviewed_at = NULL, updated_at = now()
                    WHERE id = ?
                    """, author, visibility, reviewStatus, r.docId());
            syncVectorState(r.docId(), visibility, reviewStatus);
            registerCategory(product);
            return result(r, visibility, reviewStatus);
        }
        return result(r, visibility, team ? REVIEW_PENDING : REVIEW_APPROVED);
    }

    /**
     * 文本文件发布：仅支持 .txt / .md，单文件 ≤10MB；标题缺省取文件名（去扩展名）。
     * .txt 按纯文本处理（换行即 Markdown 段落），.md 原样保留 Markdown。
     */
    @Transactional
    public UgcPublishResult publishFile(MultipartFile file, String title, String product, String version,
                                        String section, String visibility, String author) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String lower = filename.toLowerCase();
        if (!lower.endsWith(".txt") && !lower.endsWith(".md")) {
            throw new IllegalArgumentException("仅支持 .txt / .md 文本文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("文件大小不能超过 10MB");
        }
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("文件读取失败: " + e.getMessage());
        }
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        if (content.isBlank()) {
            throw new IllegalArgumentException("文件内容为空");
        }
        String docTitle = title == null || title.isBlank() ? stripExtension(filename) : title;
        return publish(new UgcPublishRequest(docTitle, content, product, version, section, visibility), author, "upload");
    }

    /** 发布前预检：只扫描不落库，供编辑器即时反馈；私有文档不扫描。 */
    public SensitiveScanService.Result scan(UgcPublishRequest req) {
        return scan(req, null);
    }

    /** 预检（带操作者）：命中同样写入拦截留痕，便于管理员事后核对。 */
    public SensitiveScanService.Result scan(UgcPublishRequest req, String username) {
        validate(req);
        if (VISIBILITY_PRIVATE.equals(normalizeVisibility(req.visibility()))) {
            return SensitiveScanService.Result.clean();
        }
        SensitiveScanService.Result result = scanRuleService.scan(req.title() + "\n" + req.markdown());
        if (result.blocked()) {
            scanAuditService.record(username, "precheck", req.title(), result);
        }
        return result;
    }

    /**
     * 编辑并重新提交：仅作者本人可操作。删除旧文档（向量 + chunk）后按新内容重新入库；
     * 团队文档重新进入待审队列（编辑后需再次审核），私有文档保持免审。
     */
    @Transactional
    public UgcPublishResult update(String docId, UgcPublishRequest req, String author) {
        validate(req);
        List<String> authors = jdbcTemplate.query("SELECT author FROM kb_document WHERE id = ?",
                (rs, rowNum) -> rs.getString("author"), docId);
        if (authors.isEmpty()) {
            throw new NotFoundException("文档不存在: " + docId);
        }
        if (authors.get(0) == null || !authors.get(0).equals(author)) {
            throw new IllegalArgumentException("仅文档作者可编辑");
        }
        String product = normalizeProduct(req.product());
        String version = normalizeVersion(req.version());
        String visibility = normalizeVisibility(req.visibility());
        boolean team = VISIBILITY_TEAM.equals(visibility);
        if (team) {
            requireClean(req.title(), req.markdown(), author, "update");
        }
        jdbcTemplate.update("DELETE FROM vector_store WHERE metadata ->> 'doc_id' = ?", docId);
        jdbcTemplate.update("DELETE FROM kb_document WHERE id = ?", docId);

        String docKey = slug(req.title()) + "-" + UUID.randomUUID().toString().substring(0, 8);
        RawDocument doc = new RawDocument(
                "ugc", product, version, "zh", "ugc", req.title().trim(), "", docKey,
                req.section(), List.of(), null, req.markdown(), req.markdown(), List.of(), List.of());
        ImportResult r = ingestionService.importDocument(doc);
        if (r.status() == ImportResult.Status.IMPORTED) {
            String reviewStatus = team ? REVIEW_PENDING : REVIEW_APPROVED;
            jdbcTemplate.update("""
                    UPDATE kb_document
                    SET author = ?, visibility = ?, review_status = ?, review_note = NULL,
                        reviewed_by = NULL, reviewed_at = NULL, updated_at = now()
                    WHERE id = ?
                    """, author, visibility, reviewStatus, r.docId());
            syncVectorState(r.docId(), visibility, reviewStatus);
            registerCategory(product);
        }
        return result(r, visibility, team ? REVIEW_PENDING : REVIEW_APPROVED);
    }

    /**
     * 切换可见性：仅作者本人或管理员可操作。
     * private → team 必须重新扫描并进入待审（防止借私有绕过审核）；team → private 免审且立即对作者可见。
     */
    @Transactional
    public String setVisibility(String docId, String visibility, String username, boolean admin) {
        String normalized = normalizeVisibility(visibility);
        List<String[]> rows = jdbcTemplate.query(
                "SELECT author, visibility FROM kb_document WHERE id = ?",
                (rs, rowNum) -> new String[]{rs.getString("author"), rs.getString("visibility")}, docId);
        if (rows.isEmpty()) {
            throw new NotFoundException("文档不存在: " + docId);
        }
        String author = rows.get(0)[0];
        String current = rows.get(0)[1];
        if (!admin && (author == null || !author.equals(username))) {
            throw new IllegalArgumentException("仅文档作者或管理员可修改可见性");
        }
        if (normalized.equals(current)) {
            return REVIEW_APPROVED;
        }
        if (VISIBILITY_PRIVATE.equals(normalized)) {
            jdbcTemplate.update("""
                    UPDATE kb_document
                    SET visibility = 'private', review_status = 'approved', review_note = NULL,
                        reviewed_by = NULL, reviewed_at = NULL, updated_at = now()
                    WHERE id = ?
                    """, docId);
            syncVectorState(docId, VISIBILITY_PRIVATE, REVIEW_APPROVED);
            return REVIEW_APPROVED;
        }
        requireClean(titleOf(docId), contentOf(docId), username, "visibility");
        jdbcTemplate.update("""
                UPDATE kb_document
                SET visibility = 'team', review_status = 'pending', review_note = NULL,
                    reviewed_by = NULL, reviewed_at = NULL, updated_at = now()
                WHERE id = ?
                """, docId);
        syncVectorState(docId, VISIBILITY_TEAM, REVIEW_PENDING);
        return REVIEW_PENDING;
    }

    /** 删除自己的文档（管理员走管理端删除接口）。 */
    @Transactional
    public void delete(String docId, String username) {
        List<String> authors = jdbcTemplate.query("SELECT author FROM kb_document WHERE id = ?",
                (rs, rowNum) -> rs.getString("author"), docId);
        if (authors.isEmpty()) {
            throw new NotFoundException("文档不存在: " + docId);
        }
        if (authors.get(0) == null || !authors.get(0).equals(username)) {
            throw new IllegalArgumentException("仅文档作者可删除");
        }
        jdbcTemplate.update("DELETE FROM vector_store WHERE metadata ->> 'doc_id' = ?", docId);
        jdbcTemplate.update("DELETE FROM kb_document WHERE id = ?", docId);
    }

    private UgcPublishResult result(ImportResult r, String visibility, String reviewStatus) {
        String message = REVIEW_PENDING.equals(reviewStatus)
                ? "已通过脱敏扫描，提交管理员审核；审核通过后才会进入知识库"
                : "私有文档已发布，仅你自己可见";
        return new UgcPublishResult(r.docId(), r.chunkCount(), visibility, reviewStatus, message);
    }

    private void requireClean(String title, String markdown, String username, String action) {
        SensitiveScanService.Result result = scanRuleService.scan(title + "\n" + markdown);
        if (result.blocked()) {
            scanAuditService.record(username, action, title, result);
            throw new SensitiveScanException(result);
        }
    }

    private String titleOf(String docId) {
        List<String> titles = jdbcTemplate.query("SELECT title FROM kb_document WHERE id = ?",
                (rs, rowNum) -> rs.getString("title"), docId);
        return titles.isEmpty() ? "" : titles.get(0);
    }

    private String contentOf(String docId) {
        return jdbcTemplate.query(
                        "SELECT content_markdown FROM kb_chunk WHERE doc_id = ? ORDER BY ordinal",
                        (rs, rowNum) -> rs.getString("content_markdown"), docId)
                .stream()
                .filter(s -> s != null && !s.isBlank())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }

    /** 把分类写入注册表（幂等），让自定义技术分类立即可在筛选器中看到。 */
    private void registerCategory(String product) {
        jdbcTemplate.update("""
                INSERT INTO kb_category (product, display_name) VALUES (?, ?)
                ON CONFLICT (product) DO NOTHING
                """, product, product);
    }

    private void syncVectorState(String docId, String visibility, String reviewStatus) {
        jdbcTemplate.update("""
                UPDATE vector_store SET metadata = jsonb_set(
                    jsonb_set(metadata::jsonb, '{visibility}', to_jsonb(?::text)),
                    '{review_status}', to_jsonb(?::text))
                WHERE metadata ->> 'doc_id' = ?
                """, visibility, reviewStatus, docId);
    }

    private static void validate(UgcPublishRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (req.title() == null || req.title().isBlank()) {
            throw new IllegalArgumentException("标题必填");
        }
        if (req.markdown() == null || req.markdown().isBlank()) {
            throw new IllegalArgumentException("正文必填");
        }
    }

    static String normalizeProduct(String product) {
        return product == null || product.isBlank() ? UNCATEGORIZED_PRODUCT : product.trim();
    }

    static String normalizeVersion(String version) {
        return version == null || version.isBlank() ? UNVERSIONED : version.trim();
    }

    static String normalizeVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return VISIBILITY_TEAM;
        }
        String v = visibility.trim().toLowerCase();
        if (!VISIBILITY_TEAM.equals(v) && !VISIBILITY_PRIVATE.equals(v)) {
            throw new IllegalArgumentException("visibility 仅支持 team / private");
        }
        return v;
    }

    /** 标题 → docKey 片段：保留中文与 ASCII，其余字符折叠为 -；空标题退化 doc。 */
    private static String slug(String title) {
        String base = title.trim()
                .replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isBlank()) {
            base = "doc";
        }
        return base.substring(0, Math.min(40, base.length()));
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        return base.isBlank() ? "未命名文档" : base;
    }
}
