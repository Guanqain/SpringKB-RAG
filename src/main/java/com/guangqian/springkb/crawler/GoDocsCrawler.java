package com.guangqian.springkb.crawler;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Go 官方文档（go.dev/doc）。站点无 sitemap，从文档首页做两跳 BFS。
 */
@Component
public class GoDocsCrawler extends AbstractIndexCrawler {

    private static final String ENTRY = "https://go.dev/doc/";

    @Override
    public String source() {
        return "go";
    }

    @Override
    protected String product() {
        return "go";
    }

    @Override
    protected List<String> entryPages() {
        return List.of(ENTRY);
    }

    @Override
    protected String contentSelector() {
        return "article.Doc.Article";
    }

    @Override
    protected boolean isDocPage(String url) {
        return url.startsWith(ENTRY) && !url.endsWith("/doc/");
    }

    @Override
    protected String docKey(String url) {
        String path = url.substring("https://go.dev/".length()).replaceFirst("/+$", "");
        return path.isEmpty() ? null : path;
    }

    @Override
    protected String docType(String key) {
        if (key.startsWith("doc/tutorial/")) {
            return "tutorial";
        }
        if (key.startsWith("doc/")) {
            return "guide";
        }
        return "reference";
    }

    @Override
    protected String cleanTitle(String title) {
        return HtmlText.normalize(title).replaceFirst(" - The Go Programming Language$", "").trim();
    }
}
