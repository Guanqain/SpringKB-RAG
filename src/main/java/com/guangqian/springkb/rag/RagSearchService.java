package com.guangqian.springkb.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * 版本过滤检索（能力一）。
 *
 * <p>过滤条件（active / version）通过 filter expression 在 pgvector 查询层下推
 * （转换为 SQL WHERE 子句，见 §6.2），禁止在 Java 内存里先召回再过滤。</p>
 *
 * <p>version 校验说明：Spring AI 2.0 的 filter 表达式（ANTLR 解析 → jsonpath）字符串
 * 字面量不支持 SQL 风格的 {@code ''} 转义，实测含单引号的表达式会在解析层抛
 * {@code FilterExpressionParseException}（不是 SQL 层错误）。因此对含单引号、换行、
 * 分号、{@code &&}/{@code ||} 的 version 一律拒绝（抛 IllegalArgumentException），
 * 保证恶意 version 不产生 SQL 错误、不注入、不越权召回。</p>
 */
@Service
public class RagSearchService {

    private static final Logger log = LoggerFactory.getLogger(RagSearchService.class);

    /** 相似度阈值兜底属于 Task 4，本任务固定 0.0（仅过滤余弦相似度为负的噪声）。 */
    private static final double SIMILARITY_THRESHOLD = 0.0d;

    /** 返回行 content 字段截断长度。 */
    private static final int CONTENT_MAX_CHARS = 200;

    /** 非法 version 字符：单引号 / 换行 / 回车 / 分号 / && / ||。 */
    private static final Pattern ILLEGAL_VERSION = Pattern.compile("['\r\n;]|&&|\\|\\|");

    private final VectorStore vectorStore;

    private final Reranker reranker;

    public RagSearchService(VectorStore vectorStore, Reranker reranker) {
        this.vectorStore = vectorStore;
        this.reranker = reranker;
    }

    /**
     * 版本过滤检索（权威性加权重排），返回 §5.2 对齐的结果行：
     * id / score / content / source(=authority) / version / title / doc_id / active。
     *
     * <p>流程（§6.3）：初检取 topK×3 → Reranker 按 boost 重排 → 截取前 topK，
     * 输出重排后结果（§5.2）。</p>
     */
    public List<Map<String, Object>> search(String query, String version, int topK) {
        int initialTopK = Math.max(1, topK * 3);
        List<Document> candidates = searchDocuments(query, version, initialTopK);
        return rerankAndTop(candidates, topK).stream().map(this::toRow).toList();
    }

    /**
     * 版本过滤检索，返回初检原始候选 Document 列表（含完整 metadata），不做重排，
     * 供后续 Task 4（兜底判定/生成）复用。
     */
    public List<Document> searchDocuments(String query, String version, int topK) {
        String filter = buildFilter(version);
        log.debug("检索 query={} version={} topK={} filter={}", query, version, topK, filter);
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .filterExpression(filter)
                .build());
    }

    /**
     * 对初检候选先做权威性加权重排（Reranker），再截取前 topK（§6.3 步骤 4~5）。
     * 供 {@link #search} 与后续 ask 链路复用；候选数不足 topK 时返回全部（不越界）。
     */
    public List<Document> rerankAndTop(List<Document> candidates, int topK) {
        List<Document> reranked = reranker.rerank(candidates);
        if (topK <= 0 || reranked.size() <= topK) {
            return reranked;
        }
        return new ArrayList<>(reranked.subList(0, topK));
    }

    /**
     * 构造 filter expression：恒有
     * {@code active == 'true' && visibility == 'team' && review_status == 'approved'}；
     * version 非空时追加 {@code && version == '<v>'}。
     *
     * <p>可见性与审核状态在检索层强制下推：private 文档与待审/驳回文档永远不会进入问答候选，
     * 即使提问者是作者本人（private 的语义是「不进团队知识库」，待审文档尚未通过审核）。</p>
     */
    private String buildFilter(String version) {
        String base = "active == 'true' && visibility == 'team' && review_status == 'approved'";
        if (version == null || version.isBlank()) {
            return base;
        }
        if (ILLEGAL_VERSION.matcher(version).find()) {
            throw new IllegalArgumentException("非法的 version 参数: " + version);
        }
        return base + " && version == '" + version + "'";
    }

    private Map<String, Object> toRow(Document doc) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", doc.getId());
        row.put("score", doc.getScore());
        row.put("content", truncate(doc.getText(), CONTENT_MAX_CHARS));
        Map<String, Object> meta = doc.getMetadata();
        row.put("source", meta.get("authority"));
        row.put("version", meta.get("version"));
        row.put("title", meta.get("title"));
        row.put("doc_id", meta.get("doc_id"));
        row.put("active", meta.get("active"));
        return row;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
