package com.guangqian.springkb.crawler;

import org.springframework.stereotype.Component;

/**
 * Nginx 官方英文文档（nginx.org/en/docs）。
 *
 * <p>sitemap 内的文档地址是 {@code http://} 协议，抓取时按原地址请求、按 {@code https://}
 * 重建原文链接，因此页面过滤与 docKey 提取都做了协议无关处理。</p>
 */
@Component
public class NginxDocsCrawler extends AbstractSitemapCrawler {

    private static final String BASE = "https://nginx.org/en/docs/";

    @Override
    public String source() {
        return "nginx";
    }

    @Override
    protected String baseUrl() {
        return BASE;
    }

    @Override
    protected String sitemapUrl() {
        return "https://nginx.org/sitemap.xml";
    }

    @Override
    protected String product() {
        return "nginx";
    }

    @Override
    protected String contentSelector() {
        return "#content";
    }

    @Override
    protected boolean isDocPage(String url) {
        String path = url.replaceFirst("^https?://nginx\\.org", "");
        return path.startsWith("/en/docs/") && path.endsWith(".html");
    }

    @Override
    protected String docKey(String url) {
        String path = url.replaceFirst("^https?://nginx\\.org/en/docs/", "").replaceFirst("\\.html$", "");
        return path.isEmpty() ? null : path;
    }

    @Override
    protected String docType(String key) {
        if ("index".equals(key)) {
            return "guide";
        }
        if (key.startsWith("beginners_guide") || key.startsWith("control") || key.startsWith("configuring")) {
            return "guide";
        }
        return "reference";
    }

    @Override
    protected String cleanTitle(String title) {
        return HtmlText.normalize(title);
    }
}
