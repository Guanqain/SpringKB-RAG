package com.guangqian.springkb.crawler;

import java.util.List;

/**
 * 文档源爬虫适配器：每个文档站一个实现（source adapter 模式），
 * 便于后续扩展任意数据源（Spring 全家桶 / AI 框架 / 工具文档 / 社区问答等）。
 */
public interface DocumentCrawler {

    /** 源标识，如 vue / springai / springboot */
    String source();

    /** 抓取最多 limit 篇样本文档 */
    List<RawDocument> crawlSample(int limit);
}
