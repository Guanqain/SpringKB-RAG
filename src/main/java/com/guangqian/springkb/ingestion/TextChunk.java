package com.guangqian.springkb.ingestion;

import java.util.List;

import com.guangqian.springkb.crawler.ImageRef;

/**
 * 分块结果：一个可独立向量化/存储的文本块。
 * ordinal 从 1 开始；headingPath 为该块起始位置的标题栈快照（h1 > h2 > h3）；
 * contentMarkdown 保留结构（供展示/溯源），contentText 为去语法纯文本（供嵌入）。
 */
public record TextChunk(
        int ordinal,
        String headingPath,
        String contentMarkdown,
        String contentText,
        int tokenCount,
        List<ImageRef> imageRefs
) {
}
