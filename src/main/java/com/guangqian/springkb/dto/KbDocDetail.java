package com.guangqian.springkb.dto;

/**
 * 知识库文档详情（GET /api/kb/doc/{id}）：markdown 为全文分块按 ordinal 拼接的 Markdown 正文；
 * likeCount/favoriteCount 为公开计数，liked/favorited 为当前登录用户状态（匿名访问时均为 false）。
 */
public record KbDocDetail(String id, String title, String product, String origin, String authority,
                          String version, String docKey, String section, String docType, String language,
                          int chunkCount, String lastModified, String updatedAt, String originalUrl,
                          String markdown, String author, long likeCount, long favoriteCount,
                          boolean liked, boolean favorited) {
}