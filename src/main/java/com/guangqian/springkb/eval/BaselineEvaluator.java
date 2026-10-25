package com.guangqian.springkb.eval;

import java.util.List;
import java.util.Set;

/**
 * 检索基线评测指标（纯函数，无 Spring / DB / IO 依赖，便于单测锁定口径）。
 *
 * <p>约定：{@code retrieved} 为按 doc_id 去重后的有序文档列表（检索系统实际输出顺序）；
 * {@code relevant} 为该查询的相关 doc_id 集合；rank 一律 1 基。</p>
 */
public final class BaselineEvaluator {

    private BaselineEvaluator() {
    }

    /** 首个相关文档在 retrieved 中的名次（1 基）；未命中返回 -1。 */
    public static int firstRelevantRank(List<String> retrieved, Set<String> relevant) {
        for (int i = 0; i < retrieved.size(); i++) {
            if (relevant.contains(retrieved.get(i))) {
                return i + 1;
            }
        }
        return -1;
    }

    /** Hit@k：前 k 名是否命中至少一个相关文档（0/1）。 */
    public static double hitAtK(List<String> retrieved, Set<String> relevant, int k) {
        return firstRelevantRank(retrieved, relevant) != -1
                && firstRelevantRank(retrieved, relevant) <= k ? 1.0 : 0.0;
    }

    /** Recall@k：前 k 名命中的相关文档数 / 相关文档总数。 */
    public static double recallAtK(List<String> retrieved, Set<String> relevant, int k) {
        if (relevant.isEmpty()) {
            return 0.0;
        }
        long hits = top(retrieved, k).stream().filter(relevant::contains).count();
        return (double) hits / relevant.size();
    }

    /** Precision@k：前 k 名命中的相关文档数 / k。 */
    public static double precisionAtK(List<String> retrieved, Set<String> relevant, int k) {
        int kk = Math.max(1, k);
        long hits = top(retrieved, kk).stream().filter(relevant::contains).count();
        return (double) hits / kk;
    }

    /** 单条查询的倒数排名（用于 MRR@k 聚合），未在前 k 命中返回 0。 */
    public static double reciprocalRankAtK(List<String> retrieved, Set<String> relevant, int k) {
        int rank = firstRelevantRank(retrieved, relevant);
        return (rank == -1 || rank > k) ? 0.0 : 1.0 / rank;
    }

    /** nDCG@k：二元相关（相关=1），gain 用 1/log2(rank+1)，IDCG 取理想降序。 */
    public static double ndcgAtK(List<String> retrieved, Set<String> relevant, int k) {
        List<String> top = top(retrieved, k);
        double dcg = 0.0;
        for (int i = 0; i < top.size(); i++) {
            if (relevant.contains(top.get(i))) {
                dcg += 1.0 / (Math.log(i + 2) / Math.log(2));
            }
        }
        int idealRelCount = (int) Math.min(relevant.size(), k);
        double idcg = 0.0;
        for (int i = 0; i < idealRelCount; i++) {
            idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
        }
        return idcg == 0.0 ? 0.0 : dcg / idcg;
    }

    /** 版本泄漏率：leaks 标志数组中 true 占比。 */
    public static double versionLeakRate(List<Boolean> leaks) {
        if (leaks.isEmpty()) {
            return 0.0;
        }
        long n = leaks.stream().filter(Boolean::booleanValue).count();
        return (double) n / leaks.size();
    }

    /** 兜底率：topScores 中低于阈值的占比。 */
    public static double fallbackRate(List<Double> topScores, double threshold) {
        if (topScores.isEmpty()) {
            return 0.0;
        }
        long n = topScores.stream().filter(s -> s != null && s < threshold).count();
        return (double) n / topScores.size();
    }

    /** 简单均值，空列表返回 0。 */
    public static double mean(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        return values.stream().mapToDouble(Double::doubleValue).sum() / values.size();
    }

    static List<String> top(List<String> retrieved, int k) {
        int n = Math.min(retrieved.size(), Math.max(0, k));
        return retrieved.subList(0, n);
    }
}