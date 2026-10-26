package com.guangqian.springkb.crawler;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Rust 官方教程《The Rust Programming Language》（doc.rust-lang.org/book）。无 sitemap，从书首页做两跳 BFS。
 */
@Component
public class RustDocsCrawler extends AbstractIndexCrawler {

    private static final String BASE = "https://doc.rust-lang.org/book/";

    @Override
    public String source() {
        return "rust";
    }

    @Override
    protected String product() {
        return "rust";
    }

    @Override
    protected List<String> entryPages() {
        return List.of(BASE);
    }

    @Override
    protected String contentSelector() {
        return "#mdbook-content";
    }

    @Override
    protected boolean isDocPage(String url) {
        return url.startsWith(BASE) && url.endsWith(".html") && !url.endsWith("print.html");
    }

    @Override
    protected String docKey(String url) {
        String path = url.substring(BASE.length()).replaceFirst("\\.html$", "");
        return path.isEmpty() || "index".equals(path) ? null : path;
    }

    @Override
    protected String version(String url) {
        return "book";
    }

    @Override
    protected String docType(String key) {
        return "guide";
    }

    @Override
    protected String section(String key) {
        return null;
    }

    @Override
    protected String cleanTitle(String title) {
        return HtmlText.normalize(title).replaceFirst(" - The Rust Programming Language$", "").trim();
    }
}
