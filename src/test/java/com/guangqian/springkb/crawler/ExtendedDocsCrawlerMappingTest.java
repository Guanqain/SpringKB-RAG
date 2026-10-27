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

    @Test
    void nacosLatestDocsOnly() {
        NacosDocsCrawler nacos = new NacosDocsCrawler();
        String url = "https://nacos.io/docs/latest/overview/";

        assertThat(nacos.isDocPage(url)).isTrue();
        assertThat(nacos.docKey(url)).isEqualTo("overview");
        assertThat(nacos.language(url)).isEqualTo("zh");
        assertThat(nacos.docType("overview")).isEqualTo("guide");
        assertThat(nacos.docType("admin/admin-api")).isEqualTo("howto");
        // 旧版路径与英文站不抓
        assertThat(nacos.isDocPage("https://nacos.io/zh-cn/docs/v2/what-is-nacos.html")).isFalse();
        assertThat(nacos.isDocPage("https://nacos.io/en/docs/latest/overview/")).isFalse();
    }

    @Test
    void sentinelDocs() {
        SentinelDocsCrawler sentinel = new SentinelDocsCrawler();
        String url = "https://sentinelguard.io/zh-cn/docs/quick-start.html";

        assertThat(sentinel.isDocPage(url)).isTrue();
        assertThat(sentinel.docKey(url)).isEqualTo("quick-start");
        assertThat(sentinel.language(url)).isEqualTo("zh");
        assertThat(sentinel.docKey("https://sentinelguard.io/zh-cn/docs/index.html")).isNull();
        assertThat(sentinel.isDocPage("https://sentinelguard.io/en/docs/quick-start.html")).isFalse();
    }

    @Test
    void dubboJavaSdkManualOnly() {
        DubboDocsCrawler dubbo = new DubboDocsCrawler();
        String url =
                "https://dubbo.apache.org/zh-cn/overview/mannual/java-sdk/tasks/protocols/triple/interface/";

        assertThat(dubbo.isDocPage(url)).isTrue();
        assertThat(dubbo.docKey(url)).isEqualTo("tasks/protocols/triple/interface");
        assertThat(dubbo.docType("tasks/protocols/triple/interface")).isEqualTo("howto");
        assertThat(dubbo.docType("reference-manual/faq/0/1")).isEqualTo("qa");
        assertThat(dubbo.isDocPage("https://dubbo.apache.org/zh-cn/blog/2023/01/16/x/")).isFalse();
    }

    @Test
    void seataOnlyCurrentVersion() {
        SeataDocsCrawler seata = new SeataDocsCrawler();
        String url = "https://seata.apache.org/zh-cn/docs/v2.5/overview/what-is-seata";

        assertThat(seata.isDocPage(url)).isTrue();
        assertThat(seata.docKey(url)).isEqualTo("overview/what-is-seata");
        assertThat(seata.version(url)).isEqualTo("2.5");
        assertThat(seata.language(url)).isEqualTo("zh");
        assertThat(seata.isDocPage("https://seata.apache.org/zh-cn/docs/v2.4/overview/what-is-seata")).isFalse();
    }

    @Test
    void rocketMqCurrentDocs() {
        RocketMqDocsCrawler rocketmq = new RocketMqDocsCrawler();
        String url = "https://rocketmq.apache.org/zh/docs/introduction/02concepts";

        assertThat(rocketmq.isDocPage(url)).isTrue();
        assertThat(rocketmq.docKey(url)).isEqualTo("introduction/02concepts");
        assertThat(rocketmq.docKey("https://rocketmq.apache.org/zh/docs/")).isEqualTo("index");
        assertThat(rocketmq.language(url)).isEqualTo("zh");
        assertThat(rocketmq.isDocPage("https://rocketmq.apache.org/zh/blog/2023/01/01/x")).isFalse();
        assertThat(rocketmq.isDocPage("https://rocketmq.apache.org/docs/introduction/02concepts")).isFalse();
    }

    @Test
    void elasticsearchReferenceAndQueryScope() {
        ElasticsearchDocsCrawler es = new ElasticsearchDocsCrawler();
        String apiUrl = "https://www.elastic.co/docs/reference/elasticsearch/rest-apis/api-conventions";
        String queryUrl = "https://www.elastic.co/docs/explore-analyze/query-filter/aggregations";

        assertThat(es.isDocPage(apiUrl)).isTrue();
        assertThat(es.docKey(apiUrl)).isEqualTo("reference/elasticsearch/rest-apis/api-conventions");
        assertThat(es.docType(es.docKey(apiUrl))).isEqualTo("api");
        assertThat(es.isDocPage(queryUrl)).isTrue();
        assertThat(es.docType(es.docKey(queryUrl))).isEqualTo("guide");
        // 客户端库与其它一级栏目不抓
        assertThat(es.isDocPage("https://www.elastic.co/docs/reference/elasticsearch/clients/java/laminate")).isFalse();
        assertThat(es.isDocPage("https://www.elastic.co/docs/deploy-manage/deploy/self-managed/install")).isFalse();
    }

    @Test
    void nginxDocsAcceptHttpSitemapUrls() {
        NginxDocsCrawler nginx = new NginxDocsCrawler();

        assertThat(nginx.isDocPage("http://nginx.org/en/docs/http/ngx_http_core_module.html")).isTrue();
        assertThat(nginx.docKey("http://nginx.org/en/docs/http/ngx_http_core_module.html"))
                .isEqualTo("http/ngx_http_core_module");
        assertThat(nginx.docKey("https://nginx.org/en/docs/beginners_guide.html")).isEqualTo("beginners_guide");
        assertThat(nginx.isDocPage("https://nginx.org/ru/docs/beginners_guide.html")).isFalse();
        assertThat(nginx.isDocPage("https://nginx.org/en/docs/")).isFalse();
    }
}
