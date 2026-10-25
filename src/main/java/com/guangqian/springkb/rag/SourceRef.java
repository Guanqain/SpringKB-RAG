package com.guangqian.springkb.rag;

/**
 * 引用溯源条目（§5.2）：index 从 1..n，与送模型的引用编号 [1]..[n] 一一对应；
 * source 为权威性（official / community）；snippet 为该文档合并后的原文片段（截断）。
 * docId 为文档级聚合的文档主键；originalUrl 为「打开原文」外链（无法可靠重建时为 null）。
 */
public record SourceRef(int index, String source, String version, String title,
                        String section, String headingPath, double score,
                        String docId, String originalUrl, String snippet) {
}
