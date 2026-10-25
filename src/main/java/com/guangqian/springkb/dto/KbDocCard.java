package com.guangqian.springkb.dto;

/**
 * 知识库文档卡（GET /api/kb/docs 列表项）；originalUrl 为原文外链（无法重建时为 null）；
 * author 为 UGC 文档的写入用户（爬虫/管理端导入为 null）。
 */
public record KbDocCard(String id, String title, String product, String origin, String authority,
                        String version, String section, String docType, int chunkCount,
                        String updatedAt, String originalUrl, String author) {
}