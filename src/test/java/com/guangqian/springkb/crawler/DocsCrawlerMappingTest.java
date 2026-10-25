package com.guangqian.springkb.crawler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 站点 URL 映射单测（不触网）：验证 Spring Antora 与 VitePress 两套规则的版本 / docKey / 页面过滤。
 */
class DocsCrawlerMappingTest {

    private final SpringFrameworkDocsCrawler spring = new SpringFrameworkDocsCrawler();

    private final VueRouterDocsCrawler router = new VueRouterDocsCrawler();

    @Test
    void springAntoraDerivesVersionAndDocKey() {
        String url = "https://docs.spring.io/spring-framework/reference/7.1-SNAPSHOT/core/aop-api/advice.html";

        assertThat(spring.isDocPage(url)).isTrue();
        assertThat(spring.version(url)).isEqualTo("7.1-SNAPSHOT");
        assertThat(spring.docKey(url)).isEqualTo("core/aop-api/advice");
        assertThat(spring.docType("core/aop-api/advice")).isEqualTo("reference");
        assertThat(spring.docType("api/index")).isEqualTo("api");
        assertThat(spring.section("core/aop-api/advice")).isEqualTo("core");
    }

    @Test
    void springAntoraSkipsNonDocPages() {
        assertThat(spring.isDocPage("https://docs.spring.io/spring-framework/reference/community.html")).isFalse();
        assertThat(spring.isDocPage("https://docs.spring.io/spring-framework/reference/search.html")).isFalse();
        assertThat(spring.isDocPage(
                "https://docs.spring.io/spring-framework/reference/7.1-SNAPSHOT/images/logo.html")).isFalse();
        assertThat(spring.isDocPage("https://example.com/7.1-SNAPSHOT/core.html")).isFalse();
    }

    @Test
    void vitePressDerivesDocKeyFromHtmlAndTrailingSlash() {
        assertThat(router.isDocPage("https://router.vuejs.org/guide/")).isTrue();
        assertThat(router.docKey("https://router.vuejs.org/guide/")).isEqualTo("guide");
        assertThat(router.docKey("https://router.vuejs.org/api/functions/createRouter.html"))
                .isEqualTo("api/functions/createRouter");
        assertThat(router.docType("api/functions/createRouter")).isEqualTo("api");
        assertThat(router.docType("guide/essentials/navigation")).isEqualTo("guide");
    }

    @Test
    void vitePressSkipsChineseDuplicatesAndBlog() {
        assertThat(router.isDocPage("https://router.vuejs.org/zh/guide/")).isFalse();
        assertThat(router.isDocPage("https://router.vuejs.org/blog/foo")).isFalse();
        assertThat(new ViteDocsCrawler().isDocPage("https://vite.dev/blog/announcing-vite5")).isFalse();
        assertThat(new ViteDocsCrawler().isDocPage("https://vite.dev/guide/features")).isTrue();
        assertThat(new PiniaDocsCrawler().isDocPage("https://pinia.vuejs.org/zh/core-concepts/")).isFalse();
        assertThat(new PiniaDocsCrawler().isDocPage("https://pinia.vuejs.org/core-concepts/")).isTrue();
    }
}
