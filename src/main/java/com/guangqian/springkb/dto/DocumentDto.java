package com.guangqian.springkb.dto;

/**
 * 管理端文档列表行：source 暴露权威性（official / community）；
 * product 为技术分类，visibility 为 team / private，author 为 UGC 作者（非 UGC 为 null）；
 * reviewStatus 为审核状态（approved / pending / rejected），reviewNote 为驳回理由。
 */
public record DocumentDto(
        String id,
        String title,
        String source,
        String product,
        String version,
        String visibility,
        String author,
        String reviewStatus,
        String reviewNote,
        boolean active,
        int chunkCount,
        String createdAt,
        String updatedAt) {
}
