package com.guangqian.springkb.crawler;

import java.util.List;
import java.util.regex.Pattern;

/**
 * docs.spring.io Antora 文档站通用规则：
 * 版本化 URL 形如 {@code <base>/<version>/<path>.html}（如 7.1-SNAPSHOT/core/aop-api/advice.html）。
 *
 * <p>sitemap 会同时包含「不带版本目录的当前页」和多个历史版本目录，这里只保留其中版本号最大的一版，
 * 避免同一篇文档的多个版本重复入库稀释检索；不带版本目录的页面一律跳过。</p>
 */
public abstract class AbstractSpringReferenceCrawler extends AbstractSitemapCrawler {

    /** 版本目录判定：7.1-SNAPSHOT / 6.2 / 7.2 等。 */
    private static final Pattern VERSION_SEGMENT = Pattern.compile("^\\d+(\\.\\d+)*(-[A-Za-z0-9.]+)?$");

    @Override
    protected String sitemapUrl() {
        return baseUrl() + "sitemap.xml";
    }

    @Override
    protected boolean isDocPage(String url) {
        if (!url.startsWith(baseUrl()) || !url.endsWith(".html")) {
            return false;
        }
        String rest = url.substring(baseUrl().length());
        if (rest.contains("images/") || rest.endsWith("index.html")) {
            return false;
        }
        // 只保留「版本目录/页面.html」，排除站点根级页面与不带版本的当前页
        String[] parts = rest.split("/");
        return parts.length >= 2 && isVersionSegment(parts[0]);
    }

    @Override
    protected String version(String url) {
        String first = url.substring(baseUrl().length()).split("/")[0];
        return isVersionSegment(first) ? first : "latest";
    }

    @Override
    protected String docKey(String url) {
        String rest = url.substring(baseUrl().length());
        String[] parts = rest.split("/", 2);
        String key = isVersionSegment(parts[0]) && parts.length > 1 ? parts[1] : rest;
        key = key.replaceFirst("\\.html$", "");
        return key.isEmpty() ? null : key;
    }

    @Override
    protected String docType(String key) {
        if (key.startsWith("how-to/")) {
            return "howto";
        }
        if (key.startsWith("api/")) {
            return "api";
        }
        return "reference";
    }

    @Override
    protected String section(String key) {
        int slash = key.indexOf('/');
        return slash > 0 ? key.substring(0, slash) : key;
    }

    /** 标题去掉 Antora 的 " :: Spring Framework" 之类后缀。 */
    @Override
    protected String cleanTitle(String title) {
        String t = HtmlText.normalize(title);
        int idx = t.indexOf(" :: ");
        return idx > 0 ? t.substring(0, idx).trim() : t;
    }

    @Override
    protected List<String[]> collectUrls() {
        List<String[]> all = super.collectUrls();
        String latest = all.stream()
                .map(e -> version(e[0]))
                .filter(v -> !"latest".equals(v))
                .max(String::compareTo)
                .orElse(null);
        if (latest == null) {
            return all;
        }
        return all.stream().filter(e -> latest.equals(version(e[0]))).toList();
    }

    private static boolean isVersionSegment(String segment) {
        return VERSION_SEGMENT.matcher(segment).matches();
    }
}
