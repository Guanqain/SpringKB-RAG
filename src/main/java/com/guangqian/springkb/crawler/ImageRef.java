package com.guangqian.springkb.crawler;

/**
 * 文档内图片引用（src + alt）。
 * alt 文本有语义价值（保留进正文/RAG），src 留作后续下载/OCR/多模态。
 */
public record ImageRef(String src, String alt) {
}
