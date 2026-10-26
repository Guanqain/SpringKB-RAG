package com.guangqian.springkb.crawler;

import org.springframework.stereotype.Component;

/**
 * PostgreSQL 官方文档（www.postgresql.org/docs/current）。只抓 current 版本，避免同一页面多版本重复。
 */
@Component
public class PostgreSqlDocsCrawler extends AbstractSitemapCrawler {

    private static final String BASE = "https://www.postgresql.org/docs/current/";

    @Override
    public String source() {
        return "postgresql";
    }

    @Override
    protected String baseUrl() {
        return BASE;
    }

    @Override
    protected String sitemapUrl() {
        return "https://www.postgresql.org/sitemap.xml";
    }

    @Override
    protected String product() {
        return "postgresql";
    }

    @Override
    protected String contentSelector() {
        return "#docContent";
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
    protected String version(String url) {
        return "current";
    }

    @Override
    protected String docType(String key) {
        if (key.startsWith("tutorial-")) {
            return "tutorial";
        }
        if (key.startsWith("sql-") || key.startsWith("app-") || key.startsWith("reference-")) {
            return "reference";
        }
        return "guide";
    }

    @Override
    protected String section(String key) {
        return null;
    }
}
