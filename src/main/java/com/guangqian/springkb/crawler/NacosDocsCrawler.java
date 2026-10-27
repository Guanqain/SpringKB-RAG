package com.guangqian.springkb.crawler;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Nacos 官方中文文档（nacos.io/docs/latest）。
 *
 * <p>站点 sitemap 仅收录了少量旧版页面，无法覆盖完整目录，因此改为从「概览」页做三跳 BFS：
 * 侧边栏在服务端渲染，链接可完整发现。旧的 {@code /zh-cn/docs/v2/} 路径已废弃，抓取时只保留
 * {@code /docs/latest/} 前缀，避免混入历史版本与英文页面。</p>
 */
@Component
public class NacosDocsCrawler extends AbstractIndexCrawler {

    private static final String BASE = "https://nacos.io/docs/latest/";

    private static final String ENTRY = BASE + "overview/";

    @Override
    public String source() {
        return "nacos";
    }

    @Override
    protected String product() {
        return "nacos";
    }

    @Override
    protected List<String> entryPages() {
        return List.of(ENTRY);
    }

    @Override
    protected String contentSelector() {
        return ".sl-markdown-content";
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
    protected int maxDepth() {
        return 3;
    }

    @Override
    protected int maxPages() {
        return 3000;
    }

    @Override
    protected String language(String url) {
        return "zh";
    }

    @Override
    protected String docType(String key) {
        if (key.startsWith("quick-start") || key.startsWith("quickstart")) {
            return "tutorial";
        }
        if (key.startsWith("guide/") || "overview".equals(key)) {
            return "guide";
        }
        if (key.startsWith("admin/") || key.startsWith("plugin/")) {
            return "howto";
        }
        return "reference";
    }

    @Override
    protected String cleanTitle(String title) {
        return HtmlText.normalize(title).replaceFirst(" \\| Nacos( 官网| 文档)?$", "").trim();
    }
}
