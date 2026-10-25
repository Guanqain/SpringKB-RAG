package com.guangqian.springkb.dto;

import java.util.List;

/**
 * 知识库体系树节点（GET /api/kb/tree）：产品线(product) ▸ 版本(version) ▸ 来源(source) 三级。
 * documentCount 为该层级下 active 文档累计数。
 */
public record KbTreeNode(String key, String label, String type, int documentCount, List<KbTreeNode> children) {
}