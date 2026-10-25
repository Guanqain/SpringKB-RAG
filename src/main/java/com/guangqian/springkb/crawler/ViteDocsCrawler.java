package com.guangqian.springkb.crawler;

import org.springframework.stereotype.Component;

/**
 * Vite 官方文档（vite.dev）。只抓 guide / config / plugins / changes，排除 blog 与社区页面。
 */
@Component
public class ViteDocsCrawler extends AbstractVitePressCrawler {

    private static final String BASE = "https://vite.dev/";

    @Override
    public String source() {
        return "vite";
    }

    @Override
    protected String baseUrl() {
        return BASE;
    }

    @Override
    protected String product() {
        return "vite";
    }

    @Override
    protected String titleSuffix() {
        return " | Vite";
    }

    @Override
    protected boolean acceptPath(String path) {
        return path.startsWith("guide/") || path.startsWith("config/")
                || path.startsWith("plugins/") || path.startsWith("changes/");
    }
}
