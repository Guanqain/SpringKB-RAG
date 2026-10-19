package com.guangqian.springkb.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 权威性加权重排器（能力二，§6.3）。
 *
 * <p>重排公式：{@code score' = score + (authority == 'official' ? boost : 0)}，按 score' 降序。
 * 仅对初检候选排序，不做任何过滤（版本/状态过滤仍由 pgvector 查询层下推）。</p>
 *
 * <p>排序稳定性：使用 Java 稳定排序（TimSort），score' 同分时保持初检顺序，不额外打乱；
 * 空候选返回空列表（空候选安全）。boost 默认 0.15，可用 {@code app.rag.boost} 配置
 * （环境变量 APP_RAG_BOOST 可覆盖）。</p>
 */
@Component
@ConfigurationProperties(prefix = "app.rag")
public class Reranker {

    /** 权威性 boost：official 文档在原始相似度上加该值，默认 0.15（§6.3）。 */
    private double boost = 0.15d;

    public Reranker() {
    }

    public double getBoost() {
        return boost;
    }

    public void setBoost(double boost) {
        this.boost = boost;
    }

    /** 用配置的 boost 对候选重排（score' 降序，稳定排序）。 */
    public List<Document> rerank(List<Document> candidates) {
        return rerank(candidates, boost);
    }

    /** 用指定 boost 对候选重排；空/null 候选返回空列表（空候选安全）。 */
    public List<Document> rerank(List<Document> candidates, double boost) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<Document> reranked = new ArrayList<>(candidates);
        // TimSort 稳定排序：score' 同分时保持初检顺序
        reranked.sort(Comparator.comparingDouble((Document doc) -> boostedScore(doc, boost)).reversed());
        return reranked;
    }

    private static double boostedScore(Document doc, double boost) {
        Double raw = doc.getScore();
        double score = raw == null ? 0.0d : raw;
        Object authority = doc.getMetadata().get("authority");
        return "official".equals(authority) ? score + boost : score;
    }
}
