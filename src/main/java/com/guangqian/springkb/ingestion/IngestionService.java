package com.guangqian.springkb.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import com.guangqian.springkb.crawler.ImageRef;
import com.guangqian.springkb.crawler.RawDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 导入链路核心服务：幂等检查 → 分块 → 向量化 → 写 vector_store → 写 kb_document/kb_chunk。
 * 单文档导入在同一事务内完成，任一环节失败整体回滚。
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    /** source → 权威性（官方站点爬虫为 official；stackoverflow 为 community；manual-* 为管理端手动导入）。 */
    static final Map<String, String> SOURCE_AUTHORITY = Map.ofEntries(
            Map.entry("vue", "official"),
            Map.entry("springai", "official"),
            Map.entry("springboot", "official"),
            Map.entry("springframework", "official"),
            Map.entry("springsecurity", "official"),
            Map.entry("springdatajpa", "official"),
            Map.entry("vite", "official"),
            Map.entry("vuerouter", "official"),
            Map.entry("pinia", "official"),
            Map.entry("go", "official"),
            Map.entry("python", "official"),
            Map.entry("fastapi", "official"),
            Map.entry("django", "official"),
            Map.entry("docker", "official"),
            Map.entry("kubernetes", "official"),
            Map.entry("postgresql", "official"),
            Map.entry("mysql", "official"),
            Map.entry("kafka", "official"),
            Map.entry("langchain", "official"),
            Map.entry("nodejs", "official"),
            Map.entry("typescript", "official"),
            Map.entry("react", "official"),
            Map.entry("rust", "official"),
            Map.entry("nacos", "official"),
            Map.entry("sentinel", "official"),
            Map.entry("dubbo", "official"),
            Map.entry("seata", "official"),
            Map.entry("rocketmq", "official"),
            Map.entry("elasticsearch", "official"),
            Map.entry("nginx", "official"),
            Map.entry("stackoverflow", "community"),
            Map.entry("manual-official", "official"),
            Map.entry("manual-community", "community"),
            Map.entry("ugc", "community"));

    /** source → 来源站点域名。 */
    static final Map<String, String> SOURCE_ORIGIN = Map.ofEntries(
            Map.entry("vue", "vuejs.org"),
            Map.entry("springai", "spring.io"),
            Map.entry("springboot", "spring.io"),
            Map.entry("springframework", "spring.io"),
            Map.entry("springsecurity", "spring.io"),
            Map.entry("springdatajpa", "spring.io"),
            Map.entry("vite", "vite.dev"),
            Map.entry("vuerouter", "router.vuejs.org"),
            Map.entry("pinia", "pinia.vuejs.org"),
            Map.entry("go", "go.dev"),
            Map.entry("python", "docs.python.org"),
            Map.entry("fastapi", "fastapi.tiangolo.com"),
            Map.entry("django", "docs.djangoproject.com"),
            Map.entry("docker", "docs.docker.com"),
            Map.entry("kubernetes", "kubernetes.io"),
            Map.entry("postgresql", "postgresql.org"),
            Map.entry("mysql", "dev.mysql.com"),
            Map.entry("kafka", "kafka.apache.org"),
            Map.entry("langchain", "docs.langchain.com"),
            Map.entry("nodejs", "nodejs.org"),
            Map.entry("typescript", "typescriptlang.org"),
            Map.entry("react", "zh-hans.react.dev"),
            Map.entry("rust", "doc.rust-lang.org"),
            Map.entry("nacos", "nacos.io"),
            Map.entry("sentinel", "sentinelguard.io"),
            Map.entry("dubbo", "dubbo.apache.org"),
            Map.entry("seata", "seata.apache.org"),
            Map.entry("rocketmq", "rocketmq.apache.org"),
            Map.entry("elasticsearch", "elastic.co"),
            Map.entry("nginx", "nginx.org"),
            Map.entry("stackoverflow", "stackoverflow.com"),
            Map.entry("manual-official", "manual"),
            Map.entry("manual-community", "stackoverflow.com"),
            Map.entry("ugc", "team"));

    private static final String DEFAULT_AUTHORITY = "official";
    private static final String DEFAULT_DOC_TYPE = "reference";
    private static final String DEFAULT_LANGUAGE = "en";

    /**
     * 向量化批大小：百炼 text-embedding-v4（OpenAI 兼容模式）单请求输入上限 10 条
     * （实测批量 20 报 {@code 400: ... batch size ... larger than 10}），取 8 留余量。
     * 超大文档（>8 chunk）必须分批提交。
     */
    private static final int EMBEDDING_BATCH_SIZE = 8;

    private final JdbcTemplate jdbcTemplate;
    private final VectorStore vectorStore;
    private final ChunkSplitter chunkSplitter;
    private final JsonMapper jsonMapper;
    /** 自引用代理：让 importAll 对每个文档的 importDocument 调用走代理，保证 @Transactional 生效。 */
    private final IngestionService self;

    public IngestionService(JdbcTemplate jdbcTemplate, VectorStore vectorStore,
            ChunkSplitter chunkSplitter, JsonMapper jsonMapper, @Lazy IngestionService self) {
        this.jdbcTemplate = jdbcTemplate;
        this.vectorStore = vectorStore;
        this.chunkSplitter = chunkSplitter;
        this.jsonMapper = jsonMapper;
        this.self = self;
    }

    /**
     * 导入单个文档：幂等命中或空文档返回 SKIPPED，否则入库并返回 IMPORTED。
     * 全程一个事务，失败整体回滚并向上抛异常（由调用方决定吞掉或失败）。
     */
    @Transactional
    public ImportResult importDocument(RawDocument doc) {
        String origin = originOf(doc);
        String authority = authorityOf(doc);
        // docKey 规范化可能把两篇不同原文映射成同一个 id（如 python 的 genindex / genindex-_），
        // 幂等检查按原始 doc_key 判断，因此这里还要检测 id 是否已被「别的文档」占用。
        String docId = resolveDocId(doc);

        if (exists(doc, origin, authority)) {
            log.info("幂等命中，跳过导入 docId={}", docId);
            return ImportResult.skipped(docId);
        }

        List<TextChunk> chunks = chunkSplitter.split(doc);
        if (chunks.isEmpty()) {
            log.info("文档无正文内容，跳过导入 docId={} docKey={}", docId, doc.docKey());
            return ImportResult.skipped(docId);
        }

        List<Document> aiDocs = chunks.stream()
                .map(chunk -> toAiDocument(chunk, docId, doc, origin, authority))
                .toList();
        // 分批向量化：百炼单请求上限 20 条输入，超大文档必须分批（见 devlog 2026-10-20）
        for (int i = 0; i < aiDocs.size(); i += EMBEDDING_BATCH_SIZE) {
            vectorStore.add(aiDocs.subList(i, Math.min(aiDocs.size(), i + EMBEDDING_BATCH_SIZE)));
        }

        insertDocument(doc, docId, origin, authority);
        for (TextChunk chunk : chunks) {
            insertChunk(chunk, docId);
        }
        jdbcTemplate.update("UPDATE kb_document SET chunk_count = ? WHERE id = ?", chunks.size(), docId);

        log.info("导入完成 docId={} chunkCount={}", docId, chunks.size());
        return ImportResult.imported(docId, chunks.size());
    }

    /**
     * 批量导入：遍历调用 importDocument（走代理，每个文档独立事务），失败文档记入 failed 不中断批次。
     */
    public ImportSummary importAll(List<RawDocument> docs) {
        return importAll(docs, null);
    }

    /**
     * 带进度回调的批量导入：每处理完一篇回调一次 {@code progress(已处理篇数, 累计汇总)}，
     * 供 CLI 渲染进度条使用；回调传 null 即为无进度模式。
     */
    public ImportSummary importAll(List<RawDocument> docs, BiConsumer<Integer, ImportSummary> progress) {
        if (docs == null || docs.isEmpty()) {
            return new ImportSummary(0, 0, 0);
        }
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        int processed = 0;
        for (RawDocument doc : docs) {
            try {
                ImportResult r = self.importDocument(doc);
                if (r.status() == ImportResult.Status.IMPORTED) {
                    imported++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                log.error("导入失败 source={} docKey={}", doc.source(), doc.docKey(), e);
            }
            processed++;
            if (progress != null) {
                progress.accept(processed, new ImportSummary(imported, skipped, failed));
            }
        }
        log.info("批量导入汇总 imported={} skipped={} failed={}", imported, skipped, failed);
        return new ImportSummary(imported, skipped, failed);
    }

    private boolean exists(RawDocument doc, String origin, String authority) {
        List<String> ids = jdbcTemplate.query(
                "SELECT id FROM kb_document WHERE product = ? AND origin = ? AND authority = ? AND version = ? AND doc_key = ?",
                (rs, rowNum) -> rs.getString("id"),
                doc.product(), origin, authority, doc.version(), doc.docKey());
        return !ids.isEmpty();
    }

    private Document toAiDocument(TextChunk chunk, String docId, RawDocument doc, String origin, String authority) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("doc_id", docId);
        meta.put("product", doc.product());
        meta.put("origin", origin);
        meta.put("authority", authority);
        meta.put("version", doc.version());
        meta.put("active", "true");
        // 可见性在检索层过滤：默认 team（团队可见、可引用）；UGC 发布为 private 时由 UgcService 回写
        meta.put("visibility", "team");
        // 审核状态同属下推过滤条件：爬虫/管理端导入默认 approved；UGC 团队文档由 UgcService 改为 pending
        meta.put("review_status", "approved");
        meta.put("doc_type", doc.docType() == null || doc.docType().isBlank() ? DEFAULT_DOC_TYPE : doc.docType());
        meta.put("language", doc.language() == null || doc.language().isBlank() ? DEFAULT_LANGUAGE : doc.language());
        meta.put("title", doc.title());
        if (doc.section() != null && !doc.section().isBlank()) {
            meta.put("section", doc.section());
        }
        return Document.builder()
                .id(chunkId(docId, chunk.ordinal()))
                .text(chunk.contentText())
                .metadata(meta)
                .build();
    }

    private void insertDocument(RawDocument doc, String docId, String origin, String authority) {
        jdbcTemplate.update("""
                INSERT INTO kb_document
                    (id, product, title, origin, authority, version, doc_key, doc_type, language, section,
                     active, visibility, review_status, last_modified, chunk_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, 'team', 'approved', ?, 0)
                """,
                docId,
                doc.product(),
                doc.title(),
                origin,
                authority,
                doc.version(),
                doc.docKey(),
                doc.docType() == null || doc.docType().isBlank() ? DEFAULT_DOC_TYPE : doc.docType(),
                doc.language() == null || doc.language().isBlank() ? DEFAULT_LANGUAGE : doc.language(),
                doc.section(),
                parseLastModified(doc.lastModified()));
    }

    private void insertChunk(TextChunk chunk, String docId) {
        String imageRefsJson;
        try {
            imageRefsJson = jsonMapper.writeValueAsString(chunk.imageRefs());
        } catch (Exception e) {
            throw new IllegalStateException("序列化 image_refs 失败 docId=" + docId, e);
        }
        jdbcTemplate.update("""
                INSERT INTO kb_chunk
                    (id, doc_id, ordinal, heading_path, content_markdown, content_text, token_count, image_refs)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """,
                chunkId(docId, chunk.ordinal()),
                docId,
                chunk.ordinal(),
                chunk.headingPath(),
                chunk.contentMarkdown(),
                chunk.contentText(),
                chunk.tokenCount(),
                imageRefsJson);
    }

    static String chunkId(String docId, int ordinal) {
        return "chunk-" + docId + "-" + ordinal;
    }

    /** {product}-{version}-{docKey规范化}；规范化后为空则退化为 {product}-{version}-{hash(docKey前16位)}。 */
    static String buildDocId(RawDocument doc) {
        String normalized = normalizeDocKey(doc.docKey());
        if (normalized.isEmpty()) {
            String head = doc.docKey() == null ? "" : doc.docKey();
            head = head.substring(0, Math.min(16, head.length()));
            return doc.product() + "-" + doc.version() + "-" + Integer.toHexString(head.hashCode());
        }
        return doc.product() + "-" + doc.version() + "-" + normalized;
    }

    /** docKey 规范化：去开头 /，[^a-zA-Z0-9.-] → -，压缩连续 -，去首尾 -。 */
    static String normalizeDocKey(String docKey) {
        if (docKey == null) {
            return "";
        }
        String k = docKey;
        while (k.startsWith("/")) {
            k = k.substring(1);
        }
        k = k.replaceAll("[^a-zA-Z0-9.-]", "-");
        k = k.replaceAll("-{2,}", "-");
        k = k.replaceAll("^-+|-+$", "");
        return k;
    }

    /**
     * 解析最终 docId：优先用规范化 id；若该 id 已被「docKey 不同的另一篇文档」占用（规范化碰撞，
     * 如 python 的 {@code genindex} 与 {@code genindex-_} 都变成 {@code genindex}），改用
     * {@code 规范化id-原始docKey短哈希}，保证幂等键（原始 doc_key）与主键都不冲突。
     * 已有数据的 id 保持不变，只有碰撞时才追加后缀。
     */
    private String resolveDocId(RawDocument doc) {
        String base = buildDocId(doc);
        if (!takenByOther(base, doc)) {
            return base;
        }
        String hashed = base + "-" + shortHash(doc.docKey());
        if (!takenByOther(hashed, doc)) {
            log.warn("docId 规范化碰撞，改用哈希后缀 docKey={} base={} -> {}", doc.docKey(), base, hashed);
            return hashed;
        }
        for (int i = 2; i <= 10; i++) {
            String candidate = hashed + "-" + i;
            if (!takenByOther(candidate, doc)) {
                log.warn("docId 哈希后缀仍碰撞，追加序号 docKey={} -> {}", doc.docKey(), candidate);
                return candidate;
            }
        }
        throw new IllegalStateException("无法为该文档生成唯一 docId，docKey=" + doc.docKey());
    }

    /** id 是否已被「docKey 不同」的文档占用；同 docKey 的话上面的幂等检查已命中，不会走到这里。 */
    private boolean takenByOther(String docId, RawDocument doc) {
        List<String> keys = jdbcTemplate.query("SELECT doc_key FROM kb_document WHERE id = ?",
                (rs, rowNum) -> rs.getString("doc_key"), docId);
        return !keys.isEmpty() && !Objects.equals(keys.get(0), doc.docKey());
    }

    /** 原始 docKey 的稳定短哈希（SHA-256 前 10 位十六进制），仅用于消解规范化碰撞。 */
    static String shortHash(String rawDocKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((rawDocKey == null ? "" : rawDocKey).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 10);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private String originOf(RawDocument doc) {
        return SOURCE_ORIGIN.getOrDefault(doc.source(), doc.source());
    }

    private String authorityOf(RawDocument doc) {
        return SOURCE_AUTHORITY.getOrDefault(doc.source(), DEFAULT_AUTHORITY);
    }

    private Timestamp parseLastModified(String lastModified) {
        if (lastModified == null || lastModified.isBlank()) {
            return null;
        }
        try {
            return Timestamp.from(Instant.parse(lastModified));
        } catch (Exception e) {
            log.debug("lastModified 解析失败，忽略：{}", lastModified);
            return null;
        }
    }
}
