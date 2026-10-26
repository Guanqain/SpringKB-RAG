package com.guangqian.springkb.crawler;

import org.springframework.stereotype.Component;

/**
 * LangChain Python 官方文档（docs.langchain.com/oss/python）。
 *
 * <p>python.langchain.com 已整体跳转到 docs.langchain.com，sitemap 同时含 JS / Python 文档，
 * 这里只保留 /oss/python/ 路径。</p>
 */
@Component
public class LangChainDocsCrawler extends AbstractSitemapCrawler {

    private static final String BASE = "https://docs.langchain.com/oss/python/";

    @Override
    public String source() {
        return "langchain";
    }

    @Override
    protected String baseUrl() {
        return BASE;
    }

    @Override
    protected String sitemapUrl() {
        return "https://docs.langchain.com/sitemap.xml";
    }

    @Override
    protected String product() {
        return "langchain";
    }

    @Override
    protected String contentSelector() {
        return "#content";
    }

    @Override
    protected boolean isDocPage(String url) {
        return url.startsWith(BASE);
    }

    @Override
    protected String docKey(String url) {
        String path = url.substring(BASE.length()).replaceFirst("/+$", "");
        return path.isEmpty() ? null : path;
    }

    @Override
    protected String docType(String key) {
        if (key.startsWith("concepts/") || key.startsWith("reference/")) {
            return "reference";
        }
        if (key.startsWith("quickstart") || key.startsWith("learn")) {
            return "tutorial";
        }
        return "guide";
    }

    @Override
    protected String cleanTitle(String title) {
        return HtmlText.normalize(title).replaceFirst(" - Docs by LangChain$", "").trim();
    }
}
