package com.guangqian.springkb.crawler;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stack Overflow 文档组装单测（不触网）：验证问题 + 回答的 Markdown 结构、docKey、产品映射与时间字段。
 */
class StackOverflowCrawlerTest {

    private final StackOverflowCrawler crawler = new StackOverflowCrawler(null);

    @Test
    void buildsQuestionAndAcceptedAnswerMarkdown() {
        Map<String, Object> question = Map.of(
                "question_id", 123L,
                "title", "How to configure port for a Spring Boot application",
                "link", "https://stackoverflow.com/questions/123",
                "score", 42L,
                "tags", List.of("spring-boot", "java"),
                "_tag", "spring-boot",
                "body", "<p>How to change <code>server.port</code>?</p>",
                "last_activity_date", 1_700_000_000L);
        Map<String, Object> answer = Map.of(
                "question_id", 123L,
                "score", 10L,
                "is_accepted", true,
                "body", "<p>Set <strong>server.port</strong> in application.yml.</p>");

        RawDocument doc = crawler.toDocument(question, List.of(answer));

        assertThat(doc.source()).isEqualTo("stackoverflow");
        assertThat(doc.product()).isEqualTo("spring-boot");
        assertThat(doc.version()).isEqualTo("latest");
        assertThat(doc.docType()).isEqualTo("qa");
        assertThat(doc.language()).isEqualTo("en");
        assertThat(doc.docKey()).isEqualTo("/questions/123");
        assertThat(doc.url()).isEqualTo("https://stackoverflow.com/questions/123");
        assertThat(doc.section()).isEqualTo("spring-boot");
        assertThat(doc.lastModified()).isNotBlank();

        assertThat(doc.contentMarkdown())
                .contains("# How to configure port for a Spring Boot application")
                .contains("## 问题")
                .contains("server.port")
                .contains("## 已采纳回答（票数 10）")
                .contains("server.port");
        assertThat(doc.contentText()).contains("How to change").contains("application.yml");
        assertThat(doc.headings()).isEmpty();
        assertThat(doc.images()).isEmpty();
    }

    @Test
    void mapsVueTagToVueProductAndLabelsNonAcceptedAnswer() {
        Map<String, Object> question = Map.of(
                "question_id", 456L,
                "title", "Pinia store not updating in Vue 3",
                "link", "https://stackoverflow.com/questions/456",
                "score", 8L,
                "tags", List.of("vue.js", "pinia"),
                "_tag", "vue.js",
                "body", "<p>Store state is stale.</p>",
                "last_activity_date", 1_700_000_001L);
        Map<String, Object> answer = Map.of(
                "question_id", 456L,
                "score", 3L,
                "is_accepted", false,
                "body", "<p>Use storeToRefs.</p>");

        RawDocument doc = crawler.toDocument(question, List.of(answer));

        assertThat(doc.product()).isEqualTo("vue");
        assertThat(doc.contentMarkdown()).contains("## 回答（票数 3）");
        assertThat(doc.contentMarkdown()).doesNotContain("已采纳回答");
    }

    @Test
    void toleratesEmptyBodies() {
        Map<String, Object> question = Map.of(
                "question_id", 789L,
                "title", "Empty body",
                "link", "https://stackoverflow.com/questions/789",
                "_tag", "spring-ai");

        RawDocument doc = crawler.toDocument(question, List.of(Map.of("score", 1L)));

        assertThat(doc.contentMarkdown()).contains("# Empty body");
        assertThat(doc.contentText()).contains("Empty body");
        assertThat(doc.product()).isEqualTo("spring-ai");
    }
}
