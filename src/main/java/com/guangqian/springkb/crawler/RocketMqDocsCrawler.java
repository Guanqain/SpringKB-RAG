package com.guangqian.springkb.crawler;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Apache RocketMQ 官方中文文档（rocketmq.apache.org/zh/docs）。
 *
 * <p>官方 sitemap 由 Docusaurus 生成且域名仍是占位地址（your-docusaurus-test-site.com），无法使用，
 * 因此从当前版本入口做三跳 BFS。侧边栏与正文同为服务端渲染，链接可完整发现。</p>
 */
@Component
public class RocketMqDocsCrawler extends AbstractIndexCrawler {

    private static final String BASE = "https://rocketmq.apache.org/zh/docs/";

    @Override
    public String source() {
        return "rocketmq";
    }

    @Override
    protected String product() {
        return "rocketmq";
    }

    @Override
    protected List<String> entryPages() {
        return List.of(BASE);
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
        return path.isEmpty() ? "index" : path;
    }

    @Override
    protected String section(String key) {
        return "index".equals(key) ? null : super.section(key);
    }

    @Override
    protected int maxDepth() {
        return 3;
    }

    @Override
    protected int maxPages() {
        return 2000;
    }

    @Override
    protected String language(String url) {
        return "zh";
    }

    @Override
    protected String docType(String key) {
        if ("index".equals(key) || key.startsWith("introduction/")) {
            return "guide";
        }
        if (key.startsWith("quickStart/")) {
            return "tutorial";
        }
        return "reference";
    }

    @Override
    protected String cleanTitle(String title) {
        return HtmlText.normalize(title).replaceFirst(" \\| RocketMQ$", "").trim();
    }
}
