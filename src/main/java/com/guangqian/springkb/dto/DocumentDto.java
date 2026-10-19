package com.guangqian.springkb.dto;

/**
 * 文档列表行（§5.3）：source 字段对前端暴露权威性（official / community）。
 */
public record DocumentDto(
        String id,
        String title,
        String source,
        String version,
        boolean active,
        int chunkCount,
        String createdAt,
        String updatedAt) {
}
