package com.guangqian.springkb.crawler;

/**
 * VitePress 文档站通用规则（vite.dev / router.vuejs.org / pinia.vuejs.org）：
 * URL 可能带 .html（自动生成的 API 页）或不带扩展名（手写指南页），docKey 统一去掉扩展名与尾斜杠。
 */
public abstract class AbstractVitePressCrawler extends AbstractSitemapCrawler {

    /** 站点标题后缀，如 " | Vite"。 */
    protected abstract String titleSuffix();

    /** 判断某个相对路径是否属于要抓取的文档。 */
    protected abstract boolean acceptPath(String path);

    @Override
    protected String sitemapUrl() {
        return baseUrl() + "sitemap.xml";
    }

    @Override
    protected String contentSelector() {
        return ".vp-doc, .theme-default-content, main";
    }

    @Override
    protected boolean isDocPage(String url) {
        if (!url.startsWith(baseUrl())) {
            return false;
        }
        String path = url.substring(baseUrl().length());
        if (path.contains("/images/") || path.startsWith("zh/")) {
            return false;
        }
        return acceptPath(path);
    }

    @Override
    protected String docKey(String url) {
        String path = url.substring(baseUrl().length())
                .replaceFirst("\\.html$", "")
                .replaceFirst("/+$", "");
        return path.isEmpty() ? null : path;
    }

    @Override
    protected String docType(String key) {
        if (key.startsWith("api/")) {
            return "api";
        }
        if (key.startsWith("guide/") || key.startsWith("core-concepts/")) {
            return "guide";
        }
        return "reference";
    }

    @Override
    protected String section(String key) {
        int slash = key.indexOf('/');
        return slash > 0 ? key.substring(0, slash) : key;
    }

    @Override
    protected String cleanTitle(String title) {
        String t = HtmlText.normalize(title);
        String suffix = titleSuffix();
        if (!suffix.isEmpty() && t.endsWith(suffix)) {
            t = t.substring(0, t.length() - suffix.length()).trim();
        }
        return t;
    }
}
