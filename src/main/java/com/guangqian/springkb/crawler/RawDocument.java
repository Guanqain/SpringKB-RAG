package com.guangqian.springkb.crawler;

import java.util.List;

/**
 * 统一原始文档模型（字段超集）：
 * 从不同文档站抽取的「实际可得元数据」，用于反推 kb_document 表结构设计。
 * contentText 为纯文本（Embedding 用）；contentMarkdown 保留文档结构（分块用）。
 */
public record RawDocument(
        String source,          // 源标识：vue / springai / springboot
        String product,         // 产品线：vue / spring-ai / spring-boot
        String version,         // 版本：3.x / 4.2-SNAPSHOT / latest
        String language,        // 语言：en / zh
        String docType,         // guide / api / tutorial / reference / howto
        String title,           // 页面标题
        String url,             // 完整 URL
        String docKey,          // URL path（去掉协议/域名/语言前缀）
        String section,         // 章节/目录位置（可为 null）
        List<String> breadcrumb, // 面包屑（可为空）
        String lastModified,    // 最后修改时间（sitemap lastmod，可为空）
        String contentText,     // 纯文本正文（去导航噪声，Embedding 用）
        String contentMarkdown, // 结构化 Markdown（保留标题层级/代码块/表格/图片，分块用）
        List<ImageRef> images,  // 图片引用（src + alt）
        List<HeadingNode> headings // 标题层级（分块锚点）
) {
}
