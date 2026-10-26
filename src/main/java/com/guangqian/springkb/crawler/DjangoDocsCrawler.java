package com.guangqian.springkb.crawler;

import org.springframework.stereotype.Component;

/**
 * Django 官方中文文档（docs.djangoproject.com/zh-hans/5.2）。sitemap 按语言拆分，直接取中文 sitemap。
 */
@Component
public class DjangoDocsCrawler extends AbstractSitemapCrawler {

    private static final String BASE = "https://docs.djangoproject.com/zh-hans/5.2/";

    @Override
    public String source() {
        return "django";
    }

    @Override
    protected String baseUrl() {
        return BASE;
    }

    @Override
    protected String sitemapUrl() {
        return "https://docs.djangoproject.com/sitemap-zh-hans.xml";
    }

    @Override
    protected String product() {
        return "django";
    }

    @Override
    protected String contentSelector() {
        return "article#docs-content";
    }

    @Override
    protected boolean isDocPage(String url) {
        return url.startsWith(BASE) && !url.contains("/_");
    }

    @Override
    protected String docKey(String url) {
        String path = url.substring(BASE.length()).replaceFirst("/+$", "");
        return path.isEmpty() ? null : path;
    }

    @Override
    protected String version(String url) {
        return "5.2";
    }

    @Override
    protected String language(String url) {
        return "zh";
    }

    @Override
    protected String docType(String key) {
        if (key.startsWith("howto/")) {
            return "howto";
        }
        if (key.startsWith("faq/")) {
            return "qa";
        }
        if (key.startsWith("intro/") || key.startsWith("topics/")) {
            return "guide";
        }
        return "reference";
    }

    @Override
    protected String cleanTitle(String title) {
        return HtmlText.normalize(title).replaceFirst(" \\| Django .*$", "").trim();
    }
}
