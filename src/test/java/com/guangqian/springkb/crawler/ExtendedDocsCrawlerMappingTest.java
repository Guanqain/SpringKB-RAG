package com.guangqian.springkb.crawler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 新增数据源 URL 映射纯单元测试（不触网）：验证 docKey / 版本 / 页面过滤。
 */
class ExtendedDocsCrawlerMappingTest {

    @Test
    void djangoChineseDocs() {
        DjangoDocsCrawler django = new DjangoDocsCrawler();
        String url = "https://docs.djangoproject.com/zh-hans/5.2/topics/db/models/";

        assertThat(django.isDocPage(url)).isTrue();
        assertThat(django.docKey(url)).isEqualTo("topics/db/models");
        assertThat(django.version(url)).isEqualTo("5.2");
        assertThat(django.language(url)).isEqualTo("zh");
        assertThat(django.docType("topics/db/models")).isEqualTo("guide");
        assertThat(django.isDocPage("https://docs.djangoproject.com/en/5.2/topics/db/models/")).isFalse();
    }

    @Test
    void kubernetesChineseDocs() {
        KubernetesDocsCrawler k8s = new KubernetesDocsCrawler();
        String url = "https://kubernetes.io/zh-cn/docs/concepts/overview/";

        assertThat(k8s.isDocPage(url)).isTrue();
        assertThat(k8s.docKey(url)).isEqualTo("concepts/overview");
        assertThat(k8s.language(url)).isEqualTo("zh");
        assertThat(k8s.isDocPage("https://kubernetes.io/zh-cn/blog/2024/")).isFalse();
    }

    @Test
    void postgresqlOnlyCurrentVersion() {
        PostgreSqlDocsCrawler pg = new PostgreSqlDocsCrawler();

        assertThat(pg.isDocPage("https://www.postgresql.org/docs/current/tutorial-sql.html")).isTrue();
        assertThat(pg.docKey("https://www.postgresql.org/docs/current/tutorial-sql.html")).isEqualTo("tutorial-sql");
        assertThat(pg.isDocPage("https://www.postgresql.org/docs/17/tutorial-sql.html")).isFalse();
    }

    @Test
    void kafkaOnlyCurrentMajorVersion() {
        KafkaDocsCrawler kafka = new KafkaDocsCrawler();

        assertThat(kafka.isDocPage("https://kafka.apache.org/43/design/design/")).isTrue();
        assertThat(kafka.docKey("https://kafka.apache.org/43/design/design/")).isEqualTo("design/design");
        assertThat(kafka.version("https://kafka.apache.org/43/design/design/")).isEqualTo("43");
        assertThat(kafka.isDocPage("https://kafka.apache.org/41/design/design/")).isFalse();
    }

    @Test
    void dockerSkipsGeneratedApiReference() {
        DockerDocsCrawler docker = new DockerDocsCrawler();

        assertThat(docker.isDocPage("https://docs.docker.com/get-started/")).isTrue();
        assertThat(docker.docKey("https://docs.docker.com/get-started/")).isEqualTo("get-started");
        assertThat(docker.isDocPage("https://docs.docker.com/reference/api/engine/latest/")).isFalse();
    }

    @Test
    void goDocs() {
        GoDocsCrawler go = new GoDocsCrawler();

        assertThat(go.isDocPage("https://go.dev/doc/effective_go")).isTrue();
        assertThat(go.docKey("https://go.dev/doc/effective_go")).isEqualTo("doc/effective_go");
        assertThat(go.isDocPage("https://go.dev/doc/")).isFalse();
    }

    @Test
    void pythonChineseDocs() {
        PythonDocsCrawler python = new PythonDocsCrawler();
        String url = "https://docs.python.org/zh-cn/3/tutorial/index.html";

        assertThat(python.isDocPage(url)).isTrue();
        assertThat(python.docKey(url)).isEqualTo("tutorial/index");
        assertThat(python.language(url)).isEqualTo("zh");
        assertThat(python.isDocPage("https://docs.python.org/3/tutorial/index.html")).isFalse();
    }

    @Test
    void mysqlDocs() {
        MySqlDocsCrawler mysql = new MySqlDocsCrawler();
        String url = "https://dev.mysql.com/doc/refman/8.4/en/select.html";

        assertThat(mysql.isDocPage(url)).isTrue();
        assertThat(mysql.docKey(url)).isEqualTo("select");
        assertThat(mysql.version(url)).isEqualTo("8.4");
        assertThat(mysql.docKey("https://dev.mysql.com/doc/refman/8.4/en/index.html")).isNull();
    }

    @Test
    void nodeJsDocs() {
        NodeJsDocsCrawler node = new NodeJsDocsCrawler();

        assertThat(node.isDocPage("https://nodejs.org/api/fs.html")).isTrue();
        assertThat(node.docKey("https://nodejs.org/api/fs.html")).isEqualTo("fs");
        assertThat(node.docKey("https://nodejs.org/api/index.html")).isNull();
    }

    @Test
    void fastapiAndLangChainDocs() {
        FastApiDocsCrawler fastapi = new FastApiDocsCrawler();

        assertThat(fastapi.isDocPage("https://fastapi.tiangolo.com/zh/tutorial/first-steps/")).isTrue();
        assertThat(fastapi.docKey("https://fastapi.tiangolo.com/zh/tutorial/first-steps/"))
                .isEqualTo("tutorial/first-steps");
        assertThat(fastapi.language("https://fastapi.tiangolo.com/zh/tutorial/first-steps/")).isEqualTo("zh");
        assertThat(fastapi.isDocPage("https://fastapi.tiangolo.com/zh/")).isFalse();

        LangChainDocsCrawler langchain = new LangChainDocsCrawler();
        String url = "https://docs.langchain.com/oss/python/langchain/overview";
        assertThat(langchain.isDocPage(url)).isTrue();
        assertThat(langchain.docKey(url)).isEqualTo("langchain/overview");
        assertThat(langchain.isDocPage("https://docs.langchain.com/oss/javascript/langchain/overview")).isFalse();
    }

    @Test
    void typescriptReactAndRustDocs() {
        TypeScriptDocsCrawler ts = new TypeScriptDocsCrawler();
        assertThat(ts.isDocPage("https://www.typescriptlang.org/zh/docs/handbook/2/basic-types.html")).isTrue();
        assertThat(ts.docKey("https://www.typescriptlang.org/zh/docs/handbook/2/basic-types.html"))
                .isEqualTo("handbook/2/basic-types");
        assertThat(ts.language("https://www.typescriptlang.org/zh/docs/handbook/2/basic-types.html"))
                .isEqualTo("zh");

        ReactDocsCrawler react = new ReactDocsCrawler();
        assertThat(react.isDocPage("https://zh-hans.react.dev/learn")).isTrue();
        assertThat(react.docKey("https://zh-hans.react.dev/learn")).isEqualTo("learn");
        assertThat(react.isDocPage("https://zh-hans.react.dev/blog/2024/12/05/react-19")).isFalse();

        RustDocsCrawler rust = new RustDocsCrawler();
        assertThat(rust.isDocPage("https://doc.rust-lang.org/book/ch01-00-getting-started.html")).isTrue();
        assertThat(rust.docKey("https://doc.rust-lang.org/book/ch01-00-getting-started.html"))
                .isEqualTo("ch01-00-getting-started");
    }
}
