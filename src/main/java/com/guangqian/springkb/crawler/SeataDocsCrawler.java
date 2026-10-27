package com.guangqian.springkb.crawler;

import org.springframework.stereotype.Component;

/**
 * Apache Seata 官方中文文档（seata.apache.org/zh-cn/docs）。
 *
 * <p>sitemap 同时收录了 v1.x ~ v2.5 多个版本，只抓当前稳定版 v2.5，避免同文多版本重复入库。</p>
 */
@Component
public class SeataDocsCrawler extends AbstractSitemapCrawler {

    private static final String BASE = "https://seata.apache.org/zh-cn/docs/v2.5/";

    @Override
    public String source() {
        return "seata";
    }

    @Override
    protected String baseUrl() {
        return BASE;
    }

    @Override
    protected String sitemapUrl() {
        return "https://seata.apache.org/sitemap.xml";
    }

    @Override
    protected String product() {
        return "seata";
    }

    @Override
    protected String contentSelector() {
        return "div.theme-doc-markdown";
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
    protected String version(String url) {
        return "2.5";
    }

    @Override
    protected String language(String url) {
        return "zh";
    }

    @Override
    protected String docType(String key) {
        if (key.startsWith("ops/")) {
            return "howto";
        }
        if (key.startsWith("overview/") || key.startsWith("user/")) {
            return "guide";
        }
        return "reference";
    }

    @Override
    protected String cleanTitle(String title) {
        return HtmlText.normalize(title).replaceFirst(" \\| Apache Seata$", "").trim();
    }
}
