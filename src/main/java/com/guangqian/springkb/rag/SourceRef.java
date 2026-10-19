package com.guangqian.springkb.rag;

/**
 * 引用溯源条目（§5.2）：index 从 1..n，与送模型的引用编号 [1]..[n] 一一对应；
 * source 为权威性（official / community）；snippet 为原文片段（截断）。
 */
public record SourceRef(int index, String source, String version, String title,
                        String section, String headingPath, double score, String snippet) {
}
