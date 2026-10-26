package com.guangqian.springkb.crawler;

import org.springframework.stereotype.Component;

/**
 * Kubernetes 官方中文文档（kubernetes.io/zh-cn/docs）。sitemap 按语言拆分，只取 zh-cn 的 docs 节点。
 */
@Component
public class KubernetesDocsCrawler extends AbstractSitemapCrawler {

    private static final String BASE = "https://kubernetes.io/zh-cn/docs/";

    @Override
    public String source() {
        return "kubernetes";
    }

    @Override
    protected String baseUrl() {
        return BASE;
    }

    @Override
    protected String sitemapUrl() {
        return "https://kubernetes.io/zh-cn/sitemap.xml";
    }

    @Override
    protected String product() {
        return "kubernetes";
    }

    @Override
    protected String contentSelector() {
        return "main[data-pagefind-body]";
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
        if (key.startsWith("concepts/")) {
            return "guide";
        }
        if (key.startsWith("tasks/")) {
            return "howto";
        }
        if (key.startsWith("tutorials/")) {
            return "tutorial";
        }
        return "reference";
    }

    @Override
    protected String cleanTitle(String title) {
        return HtmlText.normalize(title).replaceFirst(" \\| Kubernetes$", "").trim();
    }
}
