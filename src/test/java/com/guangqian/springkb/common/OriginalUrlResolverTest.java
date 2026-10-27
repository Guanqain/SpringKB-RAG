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
    void resolvesExpandedOfficialSources() {
        assertThat(OriginalUrlResolver.resolve("go", "go.dev", "latest", "doc/effective_go"))
                .isEqualTo("https://go.dev/doc/effective_go");
        assertThat(OriginalUrlResolver.resolve("python", "docs.python.org", "3", "tutorial/index"))
                .isEqualTo("https://docs.python.org/zh-cn/3/tutorial/index.html");
        assertThat(OriginalUrlResolver.resolve("fastapi", "fastapi.tiangolo.com", "latest", "tutorial/first-steps"))
                .isEqualTo("https://fastapi.tiangolo.com/zh/tutorial/first-steps/");
        assertThat(OriginalUrlResolver.resolve("django", "docs.djangoproject.com", "5.2", "topics/db/models"))
                .isEqualTo("https://docs.djangoproject.com/zh-hans/5.2/topics/db/models/");
        assertThat(OriginalUrlResolver.resolve("docker", "docs.docker.com", "latest", "get-started"))
                .isEqualTo("https://docs.docker.com/get-started/");
        assertThat(OriginalUrlResolver.resolve("kubernetes", "kubernetes.io", "latest", "concepts/overview"))
                .isEqualTo("https://kubernetes.io/zh-cn/docs/concepts/overview/");
        assertThat(OriginalUrlResolver.resolve("postgresql", "postgresql.org", "current", "tutorial-sql"))
                .isEqualTo("https://www.postgresql.org/docs/current/tutorial-sql.html");
        assertThat(OriginalUrlResolver.resolve("mysql", "dev.mysql.com", "8.4", "select"))
                .isEqualTo("https://dev.mysql.com/doc/refman/8.4/en/select.html");
        assertThat(OriginalUrlResolver.resolve("kafka", "kafka.apache.org", "43", "design/design"))
                .isEqualTo("https://kafka.apache.org/43/design/design/");
        assertThat(OriginalUrlResolver.resolve("langchain", "docs.langchain.com", "latest", "langchain/overview"))
                .isEqualTo("https://docs.langchain.com/oss/python/langchain/overview");
        assertThat(OriginalUrlResolver.resolve("nodejs", "nodejs.org", "latest", "fs"))
                .isEqualTo("https://nodejs.org/api/fs.html");
        assertThat(OriginalUrlResolver.resolve("typescript", "typescriptlang.org", "latest",
                "handbook/2/basic-types"))
                .isEqualTo("https://www.typescriptlang.org/zh/docs/handbook/2/basic-types.html");
        assertThat(OriginalUrlResolver.resolve("react", "zh-hans.react.dev", "latest", "learn"))
                .isEqualTo("https://zh-hans.react.dev/learn");
        assertThat(OriginalUrlResolver.resolve("rust", "doc.rust-lang.org", "book", "ch01-00-getting-started"))
                .isEqualTo("https://doc.rust-lang.org/book/ch01-00-getting-started.html");
    }

    @Test
    void resolvesMicroserviceAndMiddlewareSources() {
        assertThat(OriginalUrlResolver.resolve("nacos", "nacos.io", "latest", "overview"))
                .isEqualTo("https://nacos.io/docs/latest/overview/");
        assertThat(OriginalUrlResolver.resolve("sentinel", "sentinelguard.io", "latest", "quick-start"))
                .isEqualTo("https://sentinelguard.io/zh-cn/docs/quick-start.html");
        assertThat(OriginalUrlResolver.resolve("dubbo", "dubbo.apache.org", "latest", "tasks/protocols/triple/interface"))
                .isEqualTo("https://dubbo.apache.org/zh-cn/overview/mannual/java-sdk/tasks/protocols/triple/interface/");
        assertThat(OriginalUrlResolver.resolve("seata", "seata.apache.org", "2.5", "overview/what-is-seata"))
                .isEqualTo("https://seata.apache.org/zh-cn/docs/v2.5/overview/what-is-seata/");
        assertThat(OriginalUrlResolver.resolve("rocketmq", "rocketmq.apache.org", "latest", "index"))
                .isEqualTo("https://rocketmq.apache.org/zh/docs/");
        assertThat(OriginalUrlResolver.resolve("rocketmq", "rocketmq.apache.org", "latest", "introduction/02concepts"))
                .isEqualTo("https://rocketmq.apache.org/zh/docs/introduction/02concepts/");
        assertThat(OriginalUrlResolver.resolve("elasticsearch", "elastic.co", "latest",
                "reference/elasticsearch/rest-apis/api-conventions"))
                .isEqualTo("https://www.elastic.co/docs/reference/elasticsearch/rest-apis/api-conventions");
        assertThat(OriginalUrlResolver.resolve("nginx", "nginx.org", "latest", "http/ngx_http_core_module"))
                .isEqualTo("https://nginx.org/en/docs/http/ngx_http_core_module.html");
        assertThat(OriginalUrlResolver.resolve("nginx", "nginx.org", "latest", "index"))
                .isEqualTo("https://nginx.org/en/docs/index.html");
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