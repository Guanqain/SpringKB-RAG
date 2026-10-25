package com.guangqian.springkb.kb;

import java.util.List;
import java.util.UUID;

import com.guangqian.springkb.crawler.RawDocument;
import com.guangqian.springkb.dto.UgcPublishRequest;
import com.guangqian.springkb.ingestion.ImportResult;
import com.guangqian.springkb.ingestion.IngestionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模拟 UGC 文档上传：把用户编写的 Markdown 构造成 {@link RawDocument}（source=ugc）走现有
 * {@link IngestionService} 真实入库（分块 + 本地 embedding + 写向量库 + 写 kb_document/kb_chunk）。
 * ugc 在 IngestionService 中被映射为 authority=community、origin=team（原文外链不可重建，返回 null）。
 * 入库后在同一事务内把 kb_document.author 写为当前登录用户，供「我写过的文档」查询。
 */
@Service
public class UgcService {

    private static final String DEFAULT_VERSION = "team";

    private final IngestionService ingestionService;
    private final JdbcTemplate jdbcTemplate;

    public UgcService(IngestionService ingestionService, JdbcTemplate jdbcTemplate) {
        this.ingestionService = ingestionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 发布结果：docId 为新文档主键，chunkCount 为分块数。 */
    public record UgcPublishResult(String docId, int chunkCount) {
    }

    @Transactional
    public UgcPublishResult publish(UgcPublishRequest req, String author) {
        if (req.title() == null || req.title().isBlank()) {
            throw new IllegalArgumentException("标题必填");
        }
        if (req.markdown() == null || req.markdown().isBlank()) {
            throw new IllegalArgumentException("正文必填");
        }
        String version = (req.version() == null || req.version().isBlank()) ? DEFAULT_VERSION : req.version();
        String docKey = slug(req.title()) + "-" + UUID.randomUUID().toString().substring(0, 8);
        RawDocument doc = new RawDocument(
                "ugc", "springkb", version, "zh", "ugc", req.title(), "", docKey,
                req.section(), List.of(), null, req.markdown(), req.markdown(), List.of(), List.of());
        ImportResult r = ingestionService.importDocument(doc);
        if (r.status() == ImportResult.Status.IMPORTED && author != null && !author.isBlank()) {
            // 与 importDocument 同一事务：归属写入失败则整体回滚，避免出现「文档存在但不属于任何人」
            jdbcTemplate.update("UPDATE kb_document SET author = ? WHERE id = ?", author, r.docId());
        }
        return new UgcPublishResult(r.docId(), r.chunkCount());
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
}