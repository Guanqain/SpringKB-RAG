package com.guangqian.springkb.crawler;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Node.js 官方 API 文档（nodejs.org/api）。中文站点为前端渲染的 SPA，无法直接抓正文，因此使用官方英文版。
 */
@Component
public class NodeJsDocsCrawler extends AbstractIndexCrawler {

    private static final String BASE = "https://nodejs.org/api/";

    @Override
    public String source() {
        return "nodejs";
    }

    @Override
    protected String product() {
        return "nodejs";
    }

    @Override
    protected List<String> entryPages() {
        return List.of(BASE);
    }

    @Override
    protected int maxDepth() {
        return 1;
    }

    @Override
    protected String contentSelector() {
        return "#apicontent";
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
    protected String docType(String key) {
        return switch (key) {
            case "documentation", "synopsis", "usage", "cli", "packages" -> "guide";
            default -> "api";
        };
    }

    @Override
    protected String section(String key) {
        return key;
    }

    @Override
    protected String cleanTitle(String title) {
        return HtmlText.normalize(title).replaceFirst(" \\| Node\\.js .*Documentation$", "").trim();
    }
}
