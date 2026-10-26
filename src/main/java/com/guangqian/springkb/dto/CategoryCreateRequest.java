package com.guangqian.springkb.dto;

/**
 * 新增技术分类（POST /api/admin/categories）。
 */
public record CategoryCreateRequest(String product, String displayName) {
}
