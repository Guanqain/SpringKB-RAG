package com.guangqian.springkb.eval;

import java.util.List;

/**
 * 精选真实问句（金标集条目）。由 {@code resources/eval/queries.json} 反序列化。
 *
 * <p>{@code relevantTitles} 为期望命中的文档标题（精确匹配 {@code kb_document.title}，
 * 运行时解析为 doc_id；一个标题可能对应多个 doc_id，全部视为相关）。</p>
 *
 * @param id             查询标识
 * @param query          问题文本
 * @param version        可选目标版本（null = 不限版本）
 * @param relevantTitles 期望命中的文档标题列表（负例为空）
 * @param category       分类（spring-boot / spring-security / ... / negative）
 */
public record CuratedQuery(String id, String query, String version, List<String> relevantTitles, String category) {
}