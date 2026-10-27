package com.guangqian.springkb.crawler;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Sentinel 官方中文文档（sentinelguard.io/zh-cn/docs）。站点无 sitemap，侧边栏在服务端渲染，
 * 从「介绍」页做两跳 BFS 即可覆盖全部文档页。
 */
@Component
public class SentinelDocsCrawler extends AbstractIndexCrawler {

    private static final String BASE = "https://sentinelguard.io/zh-cn/docs/";

    private static final String ENTRY = BASE + "introduction.html";

    @Override
    public String source() {
        return "sentinel";
    }

    @Override
    protected String product() {
        return "sentinel";
    }

    @Override
    protected List<String> entryPages() {
        return List.of(ENTRY);
    }

    @Override
    protected String contentSelector() {
        return "div.doc-content";
    }

    @Override
    protected boolean isDocPage(String url) {
        return url.startsWith(BASE) && url.endsWith(".html");
    }

    @Override
    protected String docKey(String url) {
        String path = url.substring(BASE.length()).replaceFirst("\\.html$", "");
        return path.isEmpty() || "index".equals(path) ? null : path;
    }

    @Override
    protected int maxPages() {
        return 1500;
    }

    @Override
    protected String language(String url) {
        return "zh";
    }

    @Override
    protected String docType(String key) {
        if (key.startsWith("quick-start") || key.startsWith("basic-implementation")) {
            return "guide";
        }
        if (key.startsWith("developers/")) {
            return "howto";
        }
        return "reference";
    }

    @Override
    protected String cleanTitle(String title) {
        return HtmlText.normalize(title).replaceFirst(" \\| Sentinel$", "").trim();
    }
}
