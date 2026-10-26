package com.guangqian.springkb.crawler;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * TypeScript 官方中文文档（typescriptlang.org/zh/docs）。无 sitemap，从手册页做两跳 BFS。
 */
@Component
public class TypeScriptDocsCrawler extends AbstractIndexCrawler {

    private static final String BASE = "https://www.typescriptlang.org/zh/docs/";

    private static final String ENTRY =
            "https://www.typescriptlang.org/zh/docs/handbook/2/basic-types.html";

    @Override
    public String source() {
        return "typescript";
    }

    @Override
    protected String product() {
        return "typescript";
    }

    @Override
    protected List<String> entryPages() {
        return List.of(ENTRY);
    }

    @Override
    protected String contentSelector() {
        return "article";
    }

    @Override
    protected boolean isDocPage(String url) {
        return url.startsWith(BASE) && url.endsWith(".html");
    }

    @Override
    protected String docKey(String url) {
        String path = url.substring(BASE.length()).replaceFirst("\\.html$", "");
        return path.isEmpty() ? null : path;
    }

    @Override
    protected String language(String url) {
        return "zh";
    }

    @Override
    protected String docType(String key) {
        if (key.startsWith("handbook/")) {
            return "guide";
        }
        if (key.startsWith("reference/")) {
            return "reference";
        }
        return "guide";
    }

    @Override
    protected String cleanTitle(String title) {
        return HtmlText.normalize(title).replaceFirst(" - TypeScript$", "").trim();
    }
}
