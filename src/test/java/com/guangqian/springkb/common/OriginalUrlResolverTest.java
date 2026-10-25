package com.guangqian.springkb.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 原文 URL 重建纯单元测试：覆盖各站点规则与「无法重建返回 null」的保守分支。
 */
class OriginalUrlResolverTest {

    @Test
    void resolvesVuepressFamily() {
        assertThat(OriginalUrlResolver.resolve("vue", "vuejs.org", "3.x", "/guide/introduction"))
                .isEqualTo("https://vuejs.org/guide/introduction");
        assertThat(OriginalUrlResolver.resolve("vite", "vite.dev", null, "guide/why"))
                .isEqualTo("https://vite.dev/guide/why");
        assertThat(OriginalUrlResolver.resolve("vue-router", "router.vuejs.org", null, "guide/essentials"))
                .isEqualTo("https://router.vuejs.org/guide/essentials");
        assertThat(OriginalUrlResolver.resolve("pinia", "pinia.vuejs.org", null, "core-concepts/state"))
                .isEqualTo("https://pinia.vuejs.org/core-concepts/state");
    }

    @Test
    void resolvesSpringBootWithVersionAndHtml() {
        assertThat(OriginalUrlResolver.resolve("spring-boot", "spring.io", "4.2-SNAPSHOT", "actuator/enabling"))
                .isEqualTo("https://docs.spring.io/spring-boot/4.2-SNAPSHOT/reference/actuator/enabling.html");
    }

    @Test
    void resolvesSpringAiWithoutVersionOrHtml() {
        assertThat(OriginalUrlResolver.resolve("spring-ai", "spring.io", "latest", "getting-started"))
                .isEqualTo("https://docs.spring.io/spring-ai/reference/getting-started");
    }

    @Test
    void resolvesSpringReferenceFamily() {
        assertThat(OriginalUrlResolver.resolve("spring-framework", "spring.io", "7.1-SNAPSHOT", "core/aop-api/advice"))
                .isEqualTo("https://docs.spring.io/spring-framework/reference/7.1-SNAPSHOT/core/aop-api/advice.html");
        assertThat(OriginalUrlResolver.resolve("spring-security", "spring.io", "7.1-SNAPSHOT", "servlet/authentication"))
                .isEqualTo("https://docs.spring.io/spring-security/reference/7.1-SNAPSHOT/servlet/authentication.html");
        assertThat(OriginalUrlResolver.resolve("spring-data-jpa", "spring.io", "4.1", "repositories/definition"))
                .isEqualTo("https://docs.spring.io/spring-data/jpa/reference/4.1/repositories/definition.html");
    }

    @Test
    void resolvesStackOverflowQuestionOnly() {
        assertThat(OriginalUrlResolver.resolve("spring-boot", "stackoverflow.com", "latest", "/questions/12345"))
                .isEqualTo("https://stackoverflow.com/questions/12345");
        // manual-community 的自定义 docKey 无法重建 → 保守返回 null
        assertThat(OriginalUrlResolver.resolve("spring-boot", "stackoverflow.com", "latest", "SO-custom"))
                .isNull();
    }

    @Test
    void returnsNullForUnreconstructableSources() {
        // 未知站点 / manual / ugc(team)
        assertThat(OriginalUrlResolver.resolve("vue", "example.com", "3.x", "foo")).isNull();
        assertThat(OriginalUrlResolver.resolve("vue", "manual", "3.x", "foo")).isNull();
        assertThat(OriginalUrlResolver.resolve("springkb", "team", "team", "my-doc")).isNull();
        // 未知 spring 产品线
        assertThat(OriginalUrlResolver.resolve("spring-x", "spring.io", "1", "foo")).isNull();
    }

    @Test
    void returnsNullForBlankOrNullDocKey() {
        assertThat(OriginalUrlResolver.resolve("vue", "vuejs.org", "3.x", null)).isNull();
        assertThat(OriginalUrlResolver.resolve("vue", "vuejs.org", "3.x", "  ")).isNull();
        assertThat(OriginalUrlResolver.resolve("vue", "vuejs.org", "3.x", "/")).isNull();
    }

    @Test
    void returnsNullForBlankOrigin() {
        assertThat(OriginalUrlResolver.resolve("vue", null, "3.x", "guide")).isNull();
        assertThat(OriginalUrlResolver.resolve("vue", "", "3.x", "guide")).isNull();
    }
}