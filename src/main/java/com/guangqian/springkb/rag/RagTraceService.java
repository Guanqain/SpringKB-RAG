package com.guangqian.springkb.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 检索链路诊断（可观测性）。
 *
 * <p>按真实 ask 链路逐段执行并记录每段的状态、耗时与数据：
 * ① 问题向量化 → ② pgvector 初检 → ③ 兜底阈值判定 → ④ 权威性重排 → ⑤ 回源 kb_chunk/kb_document。</p>
 *
 * <p>与 {@link QaService#ask} 的区别：诊断模式**不调用 Chat 模型**（避免token消耗与副作用），
 * 只观测检索侧的数据流；每段同时打一条带 traceId 的 INFO 日志，便于在控制台对齐。</p>
 */
@Service
public class RagTraceService {

    private static final Logger log = LoggerFactory.getLogger(RagTraceService.class);

    /** 候选正文预览截断长度。 */
    private static final int PREVIEW_MAX_CHARS = 160;

    private final RagSearchService ragSearchService;

    private final EmbeddingModel embeddingModel;

    private final JdbcTemplate jdbcTemplate;

    private final double similarityThreshold;

    private final double boost;

    public RagTraceService(RagSearchService ragSearchService, EmbeddingModel embeddingModel,
            JdbcTemplate jdbcTemplate, Reranker reranker,
            @Value("${app.rag.similarity-threshold:0.65}") double similarityThreshold) {
        this.ragSearchService = ragSearchService;
        this.embeddingModel = embeddingModel;
        this.jdbcTemplate = jdbcTemplate;
        this.similarityThreshold = similarityThreshold;
        this.boost = reranker.getBoost();
    }

    /** 配置中的相似度阈值（诊断端点回显，保证与 ask 判定同源）。 */
    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    /**
     * 执行一次完整检索诊断。version 非法时由 RagSearchService 抛 IllegalArgumentException（控制器映射 400）。
     */
    public Map<String, Object> trace(String query, String version, int topK) {
        String traceId = "rag-" + UUID.randomUUID().toString().substring(0, 8);
        int k = Math.max(1, topK);
        int initialTopK = Math.max(1, k * 3);
        List<Map<String, Object>> stages = new ArrayList<>();

        log.info("[{}] 开始检索诊断 query={} version={} topK={} initialTopK={}", traceId, query, version, k, initialTopK);

        // ① 问题向量化
        long t0 = System.nanoTime();
        float[] embedding = embeddingModel.embed(query);
        long embeddingMs = elapsedMs(t0);
        Map<String, Object> embeddingDetail = new LinkedHashMap<>();
        embeddingDetail.put("dimensions", embedding.length);
        embeddingDetail.put("firstFiveDimensions", firstFive(embedding));
        embeddingDetail.put("l2Norm", round(l2Norm(embedding)));
        stages.add(stage("embedding", "问题向量化", "OK", embeddingMs, embeddingDetail));
        log.info("[{}] ① 向量化完成 dims={} 前5维={} 耗时={}ms", traceId, embedding.length,
                firstFive(embedding), embeddingMs);

        // ② 向量检索（过滤条件在 pgvector 查询层下推）
        long t1 = System.nanoTime();
        List<Document> candidates = ragSearchService.searchDocuments(query, version, initialTopK);
        long searchMs = elapsedMs(t1);
        Map<String, Object> searchDetail = new LinkedHashMap<>();
        searchDetail.put("filterExpression", buildFilterLabel(version));
        searchDetail.put("requestedTopK", initialTopK);
        searchDetail.put("returnedCount", candidates.size());
        stages.add(stage("search", "向量检索（pgvector）", "OK", searchMs, searchDetail));
        log.info("[{}] ② 初检完成 过滤={} 请求topK={} 实际返回={} 耗时={}ms", traceId,
                buildFilterLabel(version), initialTopK, candidates.size(), searchMs);

        // ③ 兜底阈值判定（与 QaService 完全一致：取初检第一条的相似度）
        double topScore = candidates.isEmpty() ? 0.0d : scoreOf(candidates.get(0));
        boolean passed = !candidates.isEmpty() && topScore >= similarityThreshold;
        Map<String, Object> thresholdDetail = new LinkedHashMap<>();
        thresholdDetail.put("topScore", round(topScore));
        thresholdDetail.put("threshold", similarityThreshold);
        thresholdDetail.put("passed", passed);
        thresholdDetail.put("gap", round(similarityThreshold - topScore));
        thresholdDetail.put("fallbackAnswer", passed ? null : QaService.NO_ANSWER);
        stages.add(stage("threshold", "兜底阈值判定", passed ? "PASS" : "BLOCKED", 0, thresholdDetail));
        log.info("[{}] ③ 阈值判定 topScore={} threshold={} 结果={}", traceId,
                round(topScore), similarityThreshold, passed ? "PASS" : "BLOCKED（将走兜底）");

        // ④ 权威性重排
        long t2 = System.nanoTime();
        List<Document> reranked = ragSearchService.rerankAndTop(candidates, k);
        long rerankMs = elapsedMs(t2);
        Map<String, Object> rerankDetail = new LinkedHashMap<>();
        rerankDetail.put("boost", boost);
        rerankDetail.put("inputCount", candidates.size());
        rerankDetail.put("outputCount", reranked.size());
        rerankDetail.put("orderChanged", orderChanged(candidates, reranked));
        stages.add(stage("rerank", "权威性重排", "OK", rerankMs, rerankDetail));
        log.info("[{}] ④ 重排完成 boost={} 输入={} 输出={} 顺序变化={} 耗时={}ms", traceId,
                boost, candidates.size(), reranked.size(), orderChanged(candidates, reranked), rerankMs);

        // ⑤ 回源 kb_chunk JOIN kb_document（对全部初检候选回源，确认 chunk 与文档表未失联）
        long t3 = System.nanoTime();
        Map<String, Map<String, Object>> resolved = resolveSources(candidates);
        long resolveMs = elapsedMs(t3);
        Map<String, Object> resolveDetail = new LinkedHashMap<>();
        resolveDetail.put("requested", candidates.size());
        resolveDetail.put("resolved", resolved.size());
        resolveDetail.put("missingIds", candidates.stream()
                .map(Document::getId)
                .filter(id -> !resolved.containsKey(id))
                .toList());
        stages.add(stage("resolve", "回源查询", resolved.size() == candidates.size() ? "OK" : "PARTIAL",
                resolveMs, resolveDetail));
        log.info("[{}] ⑤ 回源完成 请求={} 命中={} 耗时={}ms", traceId, candidates.size(), resolved.size(), resolveMs);

        // 候选明细（初检顺序，含重排后的加权分，便于对比 boost 效果）
        List<Map<String, Object>> candidateRows = new ArrayList<>();
        for (Document doc : candidates) {
            candidateRows.add(candidateRow(doc, resolved.get(doc.getId())));
        }

        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("found", passed);
        decision.put("topScore", round(topScore));
        decision.put("threshold", similarityThreshold);
        decision.put("chatInvoked", false);
        decision.put("note", passed ? "通过阈值：正式 ask 会调用模型生成答案" : "未通过阈值：正式 ask 直接返回兜底答案，不调用模型");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("traceId", traceId);
        result.put("query", query);
        result.put("version", version);
        result.put("topK", k);
        result.put("totalElapsedMs", stages.stream()
                .mapToLong(s -> ((Number) s.get("elapsedMs")).longValue())
                .sum());
        result.put("stages", stages);
        result.put("candidates", candidateRows);
        result.put("decision", decision);
        return result;
    }

    private static Map<String, Object> stage(String key, String label, String status,
            long elapsedMs, Map<String, Object> detail) {
        Map<String, Object> stage = new LinkedHashMap<>();
        stage.put("key", key);
        stage.put("label", label);
        stage.put("status", status);
        stage.put("elapsedMs", elapsedMs);
        stage.put("detail", detail);
        return stage;
    }

    private Map<String, Object> candidateRow(Document doc, Map<String, Object> resolved) {
        Map<String, Object> meta = doc.getMetadata();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", doc.getId());
        row.put("score", round(scoreOf(doc)));
        row.put("boostedScore", round("official".equals(meta.get("authority")) ? scoreOf(doc) + boost : scoreOf(doc)));
        row.put("authority", meta.get("authority"));
        row.put("version", meta.get("version"));
        row.put("title", meta.get("title"));
        // section / headingPath 只在 kb_chunk 表里，不在向量 metadata 中，取回源结果
        row.put("section", resolved == null ? null : resolved.get("section"));
        row.put("headingPath", resolved == null ? null : resolved.get("headingPath"));
        row.put("docId", meta.get("doc_id"));
        row.put("resolved", resolved != null);
        row.put("preview", truncate(doc.getText()));
        return row;
    }

    /** 批量回源：kb_chunk JOIN kb_document，返回 id → 行数据（用于确认 chunk 与文档表未失联）。 */
    private Map<String, Map<String, Object>> resolveSources(List<Document> docs) {
        if (docs.isEmpty()) {
            return Map.of();
        }
        List<String> ids = docs.stream().map(Document::getId).toList();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = """
                SELECT c.id, d.title, d.version, d.section, d.authority,
                       c.heading_path, char_length(c.content_markdown) AS content_chars
                FROM kb_chunk c JOIN kb_document d ON d.id = c.doc_id
                WHERE c.id IN (%s)
                """.formatted(placeholders);
        Map<String, Map<String, Object>> rows = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("title", rs.getString("title"));
            row.put("version", rs.getString("version"));
            row.put("section", rs.getString("section"));
            row.put("authority", rs.getString("authority"));
            row.put("headingPath", rs.getString("heading_path"));
            row.put("contentChars", rs.getInt("content_chars"));
            rows.put(rs.getString("id"), row);
        }, ids.toArray());
        return rows;
    }

    private static String buildFilterLabel(String version) {
        if (version == null || version.isBlank()) {
            return "active == 'true'";
        }
        return "active == 'true' && version == '" + version + "'";
    }

    /** 重排前后顺序是否变化（按 id 序列比较）。 */
    private static boolean orderChanged(List<Document> before, List<Document> after) {
        List<String> a = before.stream().map(Document::getId).toList();
        List<String> b = after.stream().map(Document::getId).toList();
        return !a.equals(b);
    }

    private static double scoreOf(Document doc) {
        Double score = doc.getScore();
        return score == null ? 0.0d : score;
    }

    private static List<Double> firstFive(float[] vector) {
        List<Double> values = new ArrayList<>(5);
        for (int i = 0; i < 5 && i < vector.length; i++) {
            values.add(round(vector[i]));
        }
        return values;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static double l2Norm(float[] vector) {
        double sum = 0.0d;
        for (float v : vector) {
            sum += (double) v * v;
        }
        return Math.sqrt(sum);
    }

    private static double round(double value) {
        return Math.round(value * 10000.0d) / 10000.0d;
    }

    private static double round(float value) {
        return round((double) value);
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= PREVIEW_MAX_CHARS ? text : text.substring(0, PREVIEW_MAX_CHARS) + "...";
    }
}
