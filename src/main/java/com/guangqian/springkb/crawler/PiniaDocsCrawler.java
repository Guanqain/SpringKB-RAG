package com.guangqian.springkb.crawler;

import org.springframework.stereotype.Component;

/**
 * Pinia 官方文档（pinia.vuejs.org）。抓英文核心概念、指南、Cookbook 与 API，排除 zh 重复内容。
 */
@Component
public class PiniaDocsCrawler extends AbstractVitePressCrawler {

    private static final String BASE = "https://pinia.vuejs.org/";

    @Override
    public String source() {
        return "pinia";
    }

    @Override
    protected String baseUrl() {
        return BASE;
    }

    @Override
    protected String product() {
        return "pinia";
    }

    @Override
    protected String titleSuffix() {
        return " | Pinia";
    }

    @Override
    protected boolean acceptPath(String path) {
        // 排除站点根页与空白路径
        return !path.isEmpty() && !"index.html".equals(path);
    }
}
