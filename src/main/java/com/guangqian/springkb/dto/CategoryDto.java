package com.guangqian.springkb.dto;

import java.util.List;

/**
 * 技术分类（产品线）及其版本：GET /api/kb/categories。
 * versions 由 kb_document.version 动态聚合，版本为空时统一展示「未标注」。
 */
public record CategoryDto(String product, String displayName, long documentCount, List<String> versions) {
}
