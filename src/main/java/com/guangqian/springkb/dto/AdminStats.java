package com.guangqian.springkb.dto;

import java.util.List;

/**
 * 管理端概览聚合（GET /api/admin/stats）：一条 GROUP BY 得到的全局指标与按 product 的文档数分布。
 */
public record AdminStats(
        long total,
        long active,
        long pending,
        long chunks,
        long official,
        long community,
        List<ProductCount> products) {

    /** 单个技术分类（product）的文档数。 */
    public record ProductCount(String product, long count) {
    }
}
