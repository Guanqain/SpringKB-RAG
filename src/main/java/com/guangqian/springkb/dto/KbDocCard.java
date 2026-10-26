package com.guangqian.springkb.dto;

/**
 * 知识库文档卡（GET /api/kb/docs 列表项）；originalUrl 为原文外链（无法重建时为 null）；
 * author 为 UGC 文档的写入用户（爬虫/管理端导入为 null）；
 * visibility：team（团队可见）/ private（仅作者可见，private 文档不会出现在他人列表与问答引用中）；
 * reviewStatus：approved（已通过/无需审核）/ pending（待审核）/ rejected（已驳回，仅作者可见）；
 * reviewNote 为驳回理由（作者可见）。
 */
public record KbDocCard(String id, String title, String product, String origin, String authority,
                        String version, String section, String docType, int chunkCount,
                        String updatedAt, String originalUrl, String author, String visibility,
                        String reviewStatus, String reviewNote) {
}
