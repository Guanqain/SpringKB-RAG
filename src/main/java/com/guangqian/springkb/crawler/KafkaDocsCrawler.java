package com.guangqian.springkb.crawler;

import org.springframework.stereotype.Component;

/**
 * Apache Kafka 官方文档（kafka.apache.org/43）。sitemap 含全部历史版本，只保留当前大版本 43。
 */
@Component
public class KafkaDocsCrawler extends AbstractSitemapCrawler {

    /** 当前文档版本号（Kafka 站点用 /43/ 这类版本目录）。 */
    private static final String CURRENT_VERSION = "43";

    private static final String BASE = "https://kafka.apache.org/" + CURRENT_VERSION + "/";

    @Override
    public String source() {
        return "kafka";
    }

    @Override
    protected String baseUrl() {
        return BASE;
    }

    @Override
    protected String sitemapUrl() {
        return "https://kafka.apache.org/sitemap.xml";
    }

    @Override
    protected String product() {
        return "kafka";
    }

    @Override
    protected String contentSelector() {
        return "main .td-content";
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
        return CURRENT_VERSION;
    }

    @Override
    protected String docType(String key) {
        if (key.startsWith("getting-started/")) {
            return "tutorial";
        }
        if (key.startsWith("configuration/")) {
            return "reference";
        }
        if (key.startsWith("operations/")) {
            return "howto";
        }
        return "guide";
    }

    @Override
    protected String cleanTitle(String title) {
        return HtmlText.normalize(title).replaceFirst(" - Apache Kafka$", "").trim();
    }
}
