package com.guangqian.springkb.crawler;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Python 官方中文文档（docs.python.org/zh-cn/3）。站点无 sitemap，从中文首页做两跳 BFS。
 */
@Component
public class PythonDocsCrawler extends AbstractIndexCrawler {

    private static final String BASE = "https://docs.python.org/zh-cn/3/";

    @Override
    public String source() {
        return "python";
    }

    @Override
    protected String product() {
        return "python";
    }

    @Override
    protected List<String> entryPages() {
        return List.of(BASE);
    }

    @Override
    protected String contentSelector() {
        return "div.body";
    }

    @Override
    protected boolean isDocPage(String url) {
        return url.startsWith(BASE) && url.endsWith(".html") && !url.contains("/_");
    }

    @Override
    protected String docKey(String url) {
        String path = url.substring(BASE.length()).replaceFirst("\\.html$", "");
        return path.isEmpty() ? null : path;
    }

    @Override
    protected String version(String url) {
        return "3";
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
        if (key.startsWith("library/") || key.startsWith("reference/")) {
            return "reference";
        }
        if (key.startsWith("howto/")) {
            return "howto";
        }
        return "guide";
    }

    @Override
    protected String cleanTitle(String title) {
        String t = HtmlText.normalize(title);
        int idx = t.lastIndexOf(" — Python");
        if (idx < 0) {
            idx = t.lastIndexOf(" — ");
        }
        return idx > 0 ? t.substring(0, idx).trim() : t;
    }
}
