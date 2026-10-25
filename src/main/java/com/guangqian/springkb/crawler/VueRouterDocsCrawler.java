package com.guangqian.springkb.crawler;

import org.springframework.stereotype.Component;

/**
 * Vue Router 官方文档（router.vuejs.org）。抓英文指南、API、数据加载与文件路由章节，排除 zh 重复内容。
 */
@Component
public class VueRouterDocsCrawler extends AbstractVitePressCrawler {

    private static final String BASE = "https://router.vuejs.org/";

    @Override
    public String source() {
        return "vuerouter";
    }

    @Override
    protected String baseUrl() {
        return BASE;
    }

    @Override
    protected String product() {
        return "vue-router";
    }

    @Override
    protected String titleSuffix() {
        return " | Vue Router";
    }

    @Override
    protected boolean acceptPath(String path) {
        return path.startsWith("guide/") || path.startsWith("api/")
                || path.startsWith("data-loaders/") || path.startsWith("file-based-routing/")
                || path.startsWith("experimental/");
    }
}
