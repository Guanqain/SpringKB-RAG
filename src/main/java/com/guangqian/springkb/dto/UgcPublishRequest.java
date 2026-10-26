package com.guangqian.springkb.dto;

/**
 * UGC 文档发布请求（POST /api/ugc/documents）。
 * product：技术分类（用户自定义，用于扩充知识库广度）；version：版本（可为空 → 未标注）；
 * visibility：team（团队可见、可引用）/ private（仅自己可见、永不作为引用）。
 */
public record UgcPublishRequest(String title, String markdown, String product, String version,
                                String section, String visibility) {
}
