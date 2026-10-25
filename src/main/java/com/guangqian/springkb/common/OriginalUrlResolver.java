package com.guangqian.springkb.common;

/**
 * 原文 URL 重建器：kb_document 不存原始 URL（origin 只存站点域名，doc_key 去掉了版本段与 .html 扩展名），
 * 读取时按 product / origin / version / doc_key 重建原文外链，供「来源聚合 + 原文跳转」与知识库文档详情使用。
 *
 * <p>无法可靠重建的源（manual / ugc 等 origin 为 team，或未知站点、以及 manual-community 的自定义 docKey）
 * 一律返回 {@code null}，前端据此隐藏「打开原文」，避免生成垃圾 URL。</p>
 */
public final class OriginalUrlResolver {

    private OriginalUrlResolver() {
    }

    public static String resolve(String product, String origin, String version, String docKey) {
        if (origin == null || origin.isBlank()) {
            return null;
        }
        String key = normalizeKey(docKey);
        if (key == null) {
            return null;
        }
        if ("spring.io".equals(origin)) {
            return springUrl(product, version, key);
        }
        return switch (origin) {
            case "vuejs.org" -> "https://vuejs.org/" + key;
            case "vite.dev" -> "https://vite.dev/" + key;
            case "router.vuejs.org" -> "https://router.vuejs.org/" + key;
            case "pinia.vuejs.org" -> "https://pinia.vuejs.org/" + key;
            // stackoverflow 爬虫 docKey 恒为 questions/{id}；manual-community 的自定义 docKey 无法重建 → null
            case "stackoverflow.com" -> key.startsWith("questions/") ? "https://stackoverflow.com/" + key : null;
            default -> null;
        };
    }

    /** docs.spring.io 各产品线 URL 规则（依据对应 crawler 的 BASE 与 docKey 提取规则）。 */
    private static String springUrl(String product, String version, String key) {
        String v = (version == null || version.isBlank()) ? "latest" : version;
        return switch (product == null ? "" : product) {
            case "spring-boot" -> "https://docs.spring.io/spring-boot/" + v + "/reference/" + key + ".html";
            case "spring-ai" -> "https://docs.spring.io/spring-ai/reference/" + key;
            case "spring-framework" -> "https://docs.spring.io/spring-framework/reference/" + v + "/" + key + ".html";
            case "spring-security" -> "https://docs.spring.io/spring-security/reference/" + v + "/" + key + ".html";
            case "spring-data-jpa" -> "https://docs.spring.io/spring-data/jpa/reference/" + v + "/" + key + ".html";
            default -> null;
        };
    }

    private static String normalizeKey(String docKey) {
        if (docKey == null) {
            return null;
        }
        String key = docKey;
        while (key.startsWith("/")) {
            key = key.substring(1);
        }
        if (key.isBlank()) {
            return null;
        }
        return key;
    }
}