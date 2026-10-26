package com.guangqian.springkb.dto;

/**
 * 可见性切换请求（PATCH /api/ugc/documents/{id}/visibility）。
 */
public record VisibilityRequest(String visibility) {
}
