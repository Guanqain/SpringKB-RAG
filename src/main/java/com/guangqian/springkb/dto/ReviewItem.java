package com.guangqian.springkb.dto;

/**
 * 管理端审核队列项：仅 UGC 文档（author 非空）会进入队列。
 * reviewStatus：pending / rejected / approved；reviewNote 为驳回理由。
 */
public record ReviewItem(String id, String title, String product, String version, String author,
                         String reviewStatus, String reviewNote, String reviewedBy, String reviewedAt,
                         String submittedAt, int chunkCount) {
}
