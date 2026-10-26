package com.guangqian.springkb.crawler;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * React 官方中文文档（zh-hans.react.dev）。无 sitemap，从 /learn 与 /reference 两个入口做两跳 BFS。
 */
@Component
public class ReactDocsCrawler extends AbstractIndexCrawler {

    private static final String BASE = "https://zh-hans.react.dev/";

    @Override
    public String source() {
        return "react";
    }

    @Override
    protected String product() {
        return "react";
    }

    @Override
    protected List<String> entryPages() {
        return List.of(BASE + "learn", BASE + "reference");
    }

    @Override
    protected String contentSelector() {
        return "article";
    }

    @Override
    protected boolean isDocPage(String url) {
        if (!url.startsWith(BASE) || url.contains("/blog")) {
            return false;
        }
        String path = url.substring(BASE.length());
        return path.startsWith("learn") || path.startsWith("reference");
    }

    @Override
    protected String docKey(String url) {
        String path = url.substring(BASE.length()).replaceFirst("/+$", "");
        return path.isEmpty() ? null : path;
    }

    @Override
    protected String language(String url) {
        return "zh";
    }

    @Override
    protected String docType(String key) {
        return key.startsWith("reference") ? "reference" : "guide";
    }

    @Override
    protected String cleanTitle(String title) {
        return HtmlText.normalize(title).replaceFirst(" – React$", "").trim();
    }
}
