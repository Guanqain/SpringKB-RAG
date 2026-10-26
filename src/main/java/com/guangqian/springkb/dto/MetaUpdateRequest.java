package com.guangqian.springkb.dto;

/**
 * 文档元数据编辑（PATCH /api/admin/documents/{id}/meta）：只允许改技术分类 / 版本 / 可见性。
 */
public record MetaUpdateRequest(String product, String version, String visibility) {
}
