package com.guangqian.springkb.crawler;

import org.springframework.stereotype.Component;

/**
 * Elasticsearch 官方文档（www.elastic.co/docs）。
 *
 * <p>新版文档站把内容拆到 explore-analyze / manage-data / reference 等多个一级栏目，
 * 完整抓取会混入 Kibana、Cloud 等无关内容。这里只取两块：{@code reference/elasticsearch}
 * （REST API、映射、配置等参考手册，排除各语言客户端）与 {@code explore-analyze/query-filter}
 * （查询、过滤与聚合），覆盖检索面试的核心概念。</p>
 */
@Component
public class ElasticsearchDocsCrawler extends AbstractSitemapCrawler {

    private static final String DOCS = "https://www.elastic.co/docs/";

    private static final String REFERENCE = DOCS + "reference/elasticsearch/";

    private static final String QUERY = DOCS + "explore-analyze/query-filter/";

    @Override
    public String source() {
        return "elasticsearch";
    }

    @Override
    protected String baseUrl() {
        return DOCS;
    }

    @Override
    protected String sitemapUrl() {
        return "https://www.elastic.co/docs/sitemap.xml";
    }

    @Override
    protected String product() {
        return "elasticsearch";
    }

    @Override
    protected String contentSelector() {
        return "article#markdown-content";
    }

    @Override
    protected boolean isDocPage(String url) {
        if (url.startsWith(REFERENCE)) {
            return !url.contains("/clients/");
        }
        return url.startsWith(QUERY);
    }

    @Override
    protected String docKey(String url) {
        String path = url.substring(DOCS.length()).replaceFirst("/+$", "");
        return path.isEmpty() ? null : path;
    }

    @Override
    protected String docType(String key) {
        if (key.contains("/rest-apis")) {
            return "api";
        }
        if (key.startsWith("explore-analyze/query-filter")) {
            return "guide";
        }
        return "reference";
    }

    @Override
    protected String cleanTitle(String title) {
        return HtmlText.normalize(title).replaceFirst(" \\| Elastic Docs?$", "").replaceFirst(" \\| Elastic$", "").trim();
    }
}
