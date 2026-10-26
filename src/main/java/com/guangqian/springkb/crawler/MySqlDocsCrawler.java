package com.guangqian.springkb.crawler;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * MySQL 8.4 官方参考手册（dev.mysql.com/doc/refman/8.4/en）。无 sitemap，从手册首页做三跳 BFS。
 */
@Component
public class MySqlDocsCrawler extends AbstractIndexCrawler {

    private static final String BASE = "https://dev.mysql.com/doc/refman/8.4/en/";

    @Override
    public String source() {
        return "mysql";
    }

    @Override
    protected String product() {
        return "mysql";
    }

    @Override
    protected List<String> entryPages() {
        return List.of(BASE);
    }

    @Override
    protected int maxDepth() {
        return 3;
    }

    @Override
    protected String contentSelector() {
        return "#docs-body";
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
    protected String version(String url) {
        return "8.4";
    }

    @Override
    protected String cleanTitle(String title) {
        String t = HtmlText.normalize(title);
        int idx = t.lastIndexOf(" :: ");
        return idx >= 0 ? t.substring(idx + 4).trim() : t;
    }
}
