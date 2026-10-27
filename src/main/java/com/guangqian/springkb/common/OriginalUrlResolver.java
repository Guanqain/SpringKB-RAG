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
            case "go.dev" -> "https://go.dev/" + key;
            case "docs.python.org" ->
                    "https://docs.python.org/zh-cn/" + pathVersion(version, "3") + "/" + key + ".html";
            case "fastapi.tiangolo.com" -> "https://fastapi.tiangolo.com/zh/" + key + "/";
            case "docs.djangoproject.com" ->
                    "https://docs.djangoproject.com/zh-hans/" + pathVersion(version, "5.2") + "/" + key + "/";
            case "docs.docker.com" -> "https://docs.docker.com/" + key + "/";
            case "kubernetes.io" -> "https://kubernetes.io/zh-cn/docs/" + key + "/";
            case "postgresql.org" ->
                    "https://www.postgresql.org/docs/" + pathVersion(version, "current") + "/" + key + ".html";
            case "dev.mysql.com" ->
                    "https://dev.mysql.com/doc/refman/" + pathVersion(version, "8.4") + "/en/" + key + ".html";
            case "kafka.apache.org" ->
                    "https://kafka.apache.org/" + pathVersion(version, "43") + "/" + key + "/";
            case "docs.langchain.com" -> "https://docs.langchain.com/oss/python/" + key;
            case "nodejs.org" -> "https://nodejs.org/api/" + key + ".html";
            case "typescriptlang.org" -> "https://www.typescriptlang.org/zh/docs/" + key + ".html";
            case "zh-hans.react.dev" -> "https://zh-hans.react.dev/" + key;
            case "doc.rust-lang.org" -> "https://doc.rust-lang.org/book/" + key + ".html";
            case "nacos.io" -> "https://nacos.io/docs/latest/" + key + "/";
            case "sentinelguard.io" -> "https://sentinelguard.io/zh-cn/docs/" + key + ".html";
            case "dubbo.apache.org" -> "https://dubbo.apache.org/zh-cn/overview/mannual/java-sdk/" + key + "/";
            case "seata.apache.org" ->
                    "https://seata.apache.org/zh-cn/docs/v" + pathVersion(version, "2.5") + "/" + key + "/";
            case "rocketmq.apache.org" -> "https://rocketmq.apache.org/zh/docs/" + ("index".equals(key) ? "" : key + "/");
            case "elastic.co" -> "https://www.elastic.co/docs/" + key;
            case "nginx.org" -> "https://nginx.org/en/docs/" + ("index".equals(key) ? "index.html" : key + ".html");
            default -> null;
        };
    }

    /** 版本号为空或 latest 时回落到该站点的默认版本段。 */
    private static String pathVersion(String version, String fallback) {
        return (version == null || version.isBlank() || "latest".equals(version)) ? fallback : version;
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