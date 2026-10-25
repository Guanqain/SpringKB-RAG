package com.guangqian.springkb.dto;

/**
 * 知识库导航页概览统计（GET /api/kb/overview）。
 */
public record KbOverview(long documentCount, long productCount, long versionCount, long sourceCount) {
}