package com.guangqian.springkb.crawler;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * FastAPI 官方中文文档（fastapi.tiangolo.com/zh）。
 * 官方 sitemap 只收录英文 URL，中文页需从中文入口做三跳 BFS。
 */
@Component
public class FastApiDocsCrawler extends AbstractIndexCrawler {

    private static final String BASE = "https://fastapi.tiangolo.com/zh/";

    @Override
    public String source() {
        return "fastapi";
    }

    @Override
    protected String product() {
        return "fastapi";
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
        return "article.md-content__inner";
    }

    @Override
    protected boolean isDocPage(String url) {
        return url.startsWith(BASE) && !url.endsWith("/zh/");
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
        if (key.startsWith("tutorial/")) {
            return "tutorial";
        }
        if (key.startsWith("advanced/") || key.startsWith("deployment/")) {
            return "howto";
        }
        if (key.startsWith("reference/")) {
            return "reference";
        }
        return "guide";
    }

    @Override
    protected String cleanTitle(String title) {
        return HtmlText.normalize(title).replaceFirst(" - FastAPI$", "").trim();
    }
}
