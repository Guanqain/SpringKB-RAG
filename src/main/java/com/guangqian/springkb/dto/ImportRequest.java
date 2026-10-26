package com.guangqian.springkb.dto;

/**
 * 管理端单文档导入请求（§5.3.1）。
 * source = 权威性：official / community；text 与 url 二者必填其一（official），community 必须粘贴 text；
 * product 为技术分类（缺省按 manual）。
 */
public record ImportRequest(
        String source,
        String product,
        String version,
        String title,
        String docKey,
        String text,
        String url,
        String soQuestionId,
        String soScore) {
}
