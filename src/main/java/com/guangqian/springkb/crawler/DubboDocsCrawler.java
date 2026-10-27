package com.guangqian.springkb.crawler;

import org.springframework.stereotype.Component;

/**
 * Apache Dubbo 官方中文文档（dubbo.apache.org/zh-cn）。
 *
 * <p>中文 sitemap 含大量博客与多语言 SDK 目录，这里只保留 Java SDK 手册前缀
 * {@code /zh-cn/overview/mannual/java-sdk/}（站点原文即为 mannual，属官方拼写）。</p>
 */
@Component
public class DubboDocsCrawler extends AbstractSitemapCrawler {

    private static final String BASE = "https://dubbo.apache.org/zh-cn/overview/mannual/java-sdk/";

    @Override
    public String source() {
        return "dubbo";
    }

    @Override
    protected String baseUrl() {
        return BASE;
    }

    @Override
    protected String sitemapUrl() {
        return "https://dubbo.apache.org/zh-cn/sitemap.xml";
    }

    @Override
    protected String product() {
        return "dubbo";
    }

    @Override
    protected String contentSelector() {
        return "div.td-content";
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
    protected String language(String url) {
        return "zh";
    }

    @Override
    protected String docType(String key) {
        if (key.startsWith("tasks/")) {
            return "howto";
        }
        if (key.startsWith("reference-manual/faq/")) {
            return "qa";
        }
        return key.startsWith("reference-manual/") ? "reference" : "guide";
    }

    @Override
    protected String cleanTitle(String title) {
        return HtmlText.normalize(title).replaceFirst(" \\| Apache Dubbo$", "").trim();
    }
}
