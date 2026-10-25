package com.guangqian.springkb.eval;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BaselineEvaluator 纯 JVM 单测：锁定各指标口径（可信基线的度量基准）。
 */
class BaselineEvaluatorTest {

    // 检索输出顺序（已按 doc_id 去重）：relevant = {"d2"} 在第 2 位
    private static final List<String> RETRIEVED = List.of("d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "d9", "d10");
    private static final Set<String> RELEVANT = Set.of("d2");

    private static final List<String> RETRIEVED_MULTI = List.of("a1", "b1", "a2", "c1", "b2");
    private static final Set<String> RELEVANT_MULTI = Set.of("a1", "a2");

    @Test
    void firstRelevantRankFindsEarliestMatch() {
        assertThat(BaselineEvaluator.firstRelevantRank(RETRIEVED, RELEVANT)).isEqualTo(2);
        assertThat(BaselineEvaluator.firstRelevantRank(List.of("x"), RELEVANT)).isEqualTo(-1);
    }

    @Test
    void hitAtKCountsAnyTopKMatch() {
        assertThat(BaselineEvaluator.hitAtK(RETRIEVED, RELEVANT, 1)).isEqualTo(0.0);
        assertThat(BaselineEvaluator.hitAtK(RETRIEVED, RELEVANT, 2)).isEqualTo(1.0);
        assertThat(BaselineEvaluator.hitAtK(RETRIEVED, RELEVANT, 10)).isEqualTo(1.0);
    }

    @Test
    void recallAndPrecisionUseTopK() {
        // 单一相关文档
        assertThat(BaselineEvaluator.recallAtK(RETRIEVED, RELEVANT, 2)).isEqualTo(1.0);
        assertThat(BaselineEvaluator.recallAtK(RETRIEVED, RELEVANT, 1)).isEqualTo(0.0);
        assertThat(BaselineEvaluator.precisionAtK(RETRIEVED, RELEVANT, 5)).isEqualTo(0.2);

        // 多个相关文档
        assertThat(BaselineEvaluator.recallAtK(RETRIEVED_MULTI, RELEVANT_MULTI, 5)).isEqualTo(1.0);
        assertThat(BaselineEvaluator.recallAtK(RETRIEVED_MULTI, RELEVANT_MULTI, 1)).isEqualTo(0.5);
        assertThat(BaselineEvaluator.precisionAtK(RETRIEVED_MULTI, RELEVANT_MULTI, 2)).isEqualTo(0.5);
    }

    @Test
    void reciprocalRankIsOneOverRank() {
        assertThat(BaselineEvaluator.reciprocalRankAtK(RETRIEVED, RELEVANT, 10)).isEqualTo(0.5);
        assertThat(BaselineEvaluator.reciprocalRankAtK(RETRIEVED, RELEVANT, 1)).isEqualTo(0.0);
    }

    @Test
    void ndcgIsZeroWhenMissAndPositiveWhenHit() {
        assertThat(BaselineEvaluator.ndcgAtK(List.of("x", "y"), RELEVANT, 10)).isEqualTo(0.0);
        assertThat(BaselineEvaluator.ndcgAtK(RETRIEVED, RELEVANT, 10)).isGreaterThan(0.0);
        assertThat(BaselineEvaluator.ndcgAtK(RETRIEVED, RELEVANT, 10)).isLessThanOrEqualTo(1.0);
    }

    @Test
    void versionLeakRateCountsTrueProportion() {
        assertThat(BaselineEvaluator.versionLeakRate(List.of(true, false, false, false))).isEqualTo(0.25);
        assertThat(BaselineEvaluator.versionLeakRate(List.of(false, false))).isEqualTo(0.0);
        assertThat(BaselineEvaluator.versionLeakRate(List.of())).isEqualTo(0.0);
    }

    @Test
    void fallbackRateCountsBelowThreshold() {
        assertThat(BaselineEvaluator.fallbackRate(List.of(0.7, 0.4, 0.6), 0.5)).isCloseTo(1.0 / 3.0,
                org.assertj.core.data.Offset.offset(1e-9));
        assertThat(BaselineEvaluator.fallbackRate(List.of(), 0.5)).isEqualTo(0.0);
    }

    @Test
    void meanAveragesAndHandlesEmpty() {
        assertThat(BaselineEvaluator.mean(List.of(1.0, 2.0, 3.0))).isEqualTo(2.0);
        assertThat(BaselineEvaluator.mean(List.of())).isEqualTo(0.0);
    }
}