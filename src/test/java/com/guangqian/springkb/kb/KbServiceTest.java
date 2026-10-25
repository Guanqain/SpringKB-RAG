package com.guangqian.springkb.kb;

import java.nio.charset.StandardCharsets;

import com.guangqian.springkb.demo.FakeDocumentSeeder;
import com.guangqian.springkb.dto.KbDocDetail;
import com.guangqian.springkb.dto.KbOverview;
import com.guangqian.springkb.dto.KbTreeNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 知识库导航集成测试（springkb_test 真实库）：概览、体系树、文档列表过滤与 originalUrl 重建、
 * 文档详情 Markdown 拼接与 404、公开接口 permitAll。仅操作 kb_document/kb_chunk，不依赖向量库。
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class KbServiceTest {

    @Autowired
    KbService kbService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MockMvc mockMvc;

    /** 挡住 demo seeder / 未使用 embedding。 */
    @MockitoBean
    EmbeddingModel embeddingModel;

    @MockitoBean
    FakeDocumentSeeder fakeDocumentSeeder;

    @BeforeAll
    static void ensureTestDatabase() throws Exception {
        String exists = dockerPsql("SELECT 1 FROM pg_database WHERE datname='springkb_test'");
        if (!exists.contains("1")) {
            dockerPsql("CREATE DATABASE springkb_test");
        }
        dockerPsqlDb("DELETE FROM kb_chunk");
        dockerPsqlDb("DELETE FROM kb_document");
    }

    @BeforeEach
    void clearTables() {
        jdbcTemplate.update("DELETE FROM kb_chunk");
        jdbcTemplate.update("DELETE FROM kb_document");
    }

    // ---------- 1. 概览只统计 active 文档 ----------
    @Test
    void overviewCountsActiveDocumentsOnly() {
        seedDoc("d1", "spring-boot", "spring.io", "official", "4.2-SNAPSHOT", "actuator/enabling", null, "Boot CDS", "a");
        seedDoc("d2", "spring-ai", "spring.io", "official", "latest", "getting-started", null, "AI Getting Started", "b");
        seedDoc("d3", "vue", "vuejs.org", "official", "3.x", "guide/intro", null, "Vue Intro", "c");
        jdbcTemplate.update("UPDATE kb_document SET active = false WHERE id = ?", "d3");

        KbOverview ov = kbService.overview();

        assertThat(ov.documentCount()).isEqualTo(2);
        assertThat(ov.productCount()).isEqualTo(2);
        assertThat(ov.versionCount()).isEqualTo(2);
        assertThat(ov.sourceCount()).isEqualTo(1);
    }

    // ---------- 2. 体系树按 product ▸ version ▸ source 分组 ----------
    @Test
    void treeGroupsByProductVersionSource() {
        seedDoc("d1", "spring-boot", "spring.io", "official", "4.2-SNAPSHOT", "actuator/enabling", null, "Boot CDS", "a");
        seedDoc("d2", "spring-boot", "spring.io", "official", "4.1", "actuator/enabling", null, "Boot Old", "b");
        seedDoc("d3", "spring-boot", "stackoverflow.com", "community", "latest", "/questions/1", null, "SO Question", "c");

        var roots = kbService.tree();

        assertThat(roots).hasSize(1);
        KbTreeNode boot = roots.get(0);
        assertThat(boot.key()).isEqualTo("spring-boot");
        assertThat(boot.type()).isEqualTo("product");
        assertThat(boot.documentCount()).isEqualTo(3);
        // boot 下三个版本节点（4.1 / 4.2-SNAPSHOT / latest）
        assertThat(boot.children()).hasSize(3);
        KbTreeNode v42 = boot.children().stream().filter(v -> v.key().equals("4.2-SNAPSHOT")).findFirst().orElseThrow();
        assertThat(v42.type()).isEqualTo("version");
        assertThat(v42.children()).hasSize(1);
        assertThat(v42.children().get(0).key()).isEqualTo("official");
        assertThat(v42.children().get(0).type()).isEqualTo("source");
    }

    // ---------- 3. 文档列表过滤 + originalUrl 重建 ----------
    @Test
    void docsFilterAndRebuildOriginalUrl() {
        seedDoc("d1", "spring-boot", "spring.io", "official", "4.2-SNAPSHOT", "actuator/enabling", null, "Boot CDS", "a");
        seedDoc("d2", "springkb", "team", "community", "team", "my-guide-abc", null, "团队手写指南", "b");

        var bootDocs = kbService.docs("spring-boot", null, null, null);
        assertThat(bootDocs).hasSize(1);
        assertThat(bootDocs.get(0).originalUrl())
                .isEqualTo("https://docs.spring.io/spring-boot/4.2-SNAPSHOT/reference/actuator/enabling.html");

        // ugc（origin=team）无法重建原文 → originalUrl 为 null
        var ugcDocs = kbService.docs(null, null, null, "团队");
        assertThat(ugcDocs).hasSize(1);
        assertThat(ugcDocs.get(0).originalUrl()).isNull();
    }

    // ---------- 4. 详情：全文 Markdown 按 ordinal 拼接 + 404 ----------
    @Test
    void docDetailConcatenatesMarkdownAnd404() {
        String docId = "d1";
        seedDoc(docId, "spring-boot", "spring.io", "official", "4.2-SNAPSHOT", "actuator/enabling", null, "Boot CDS",
                "## 段落一", "## 段落二");

        KbDocDetail detail = kbService.doc(docId);

        assertThat(detail.title()).isEqualTo("Boot CDS");
        assertThat(detail.markdown()).isEqualTo("## 段落一\n\n## 段落二");
        assertThat(detail.originalUrl())
                .isEqualTo("https://docs.spring.io/spring-boot/4.2-SNAPSHOT/reference/actuator/enabling.html");

        assertThatThrownBy(() -> kbService.doc("missing"))
                .isInstanceOf(com.guangqian.springkb.common.NotFoundException.class)
                .hasMessageContaining("文档不存在");
    }

    // ---------- 3.1 分页：total 与 LIMIT/OFFSET 切页 ----------
    @Test
    void docsPageReturnsTotalAndSlicesByLimitOffset() {
        for (int i = 1; i <= 5; i++) {
            seedDoc("p" + i, "spring-boot", "spring.io", "official", "4.2-SNAPSHOT", "actuator/" + i, null, "文档 " + i, "## x");
        }

        var first = kbService.docsPage("spring-boot", null, null, null, 1, 2);
        assertThat(first.total()).isEqualTo(5);
        assertThat(first.page()).isEqualTo(1);
        assertThat(first.size()).isEqualTo(2);
        assertThat(first.list()).hasSize(2);

        var last = kbService.docsPage("spring-boot", null, null, null, 3, 2);
        assertThat(last.list()).hasSize(1);

        // 越界页返回空列表，total 不变
        var beyond = kbService.docsPage("spring-boot", null, null, null, 99, 2);
        assertThat(beyond.total()).isEqualTo(5);
        assertThat(beyond.list()).isEmpty();

        // size 超上限钳到 100，非法 page 钳到 1
        var clamped = kbService.docsPage(null, null, null, null, 0, 999);
        assertThat(clamped.page()).isEqualTo(1);
        assertThat(clamped.size()).isEqualTo(100);
        assertThat(clamped.total()).isEqualTo(5);
    }

    // ---------- 5. /api/kb/** permitAll：未登录也能访问覆盖 ----------
    @Test
    void kbOverviewIsPublic() throws Exception {
        mockMvc.perform(get("/api/kb/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.documentCount").isNumber());
    }

    // ---------- helpers ----------

    private void seedDoc(String docId, String product, String origin, String authority, String version,
            String docKey, String section, String title, String... markdowns) {
        jdbcTemplate.update("""
                INSERT INTO kb_document
                    (id, product, title, origin, authority, version, doc_key, doc_type, language, section, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'reference', 'en', ?, true)
                """, docId, product, title, origin, authority, version, docKey, section);
        for (int i = 0; i < markdowns.length; i++) {
            jdbcTemplate.update("""
                    INSERT INTO kb_chunk (id, doc_id, ordinal, heading_path, content_markdown, content_text, token_count, image_refs)
                    VALUES (?, ?, ?, '', ?, ?, 0, '[]'::jsonb)
                    """, docId + "-c" + i, docId, i, markdowns[i], markdowns[i]);
        }
    }

    private static String dockerPsql(String sql) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("docker", "exec", "rag-postgres", "psql", "-U", "postgres", "-t", "-A", "-c", sql);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        if (code != 0) {
            throw new IllegalStateException("docker exec psql 失败: " + out);
        }
        return out.trim();
    }

    private static String dockerPsqlDb(String sql) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("docker", "exec", "rag-postgres", "psql", "-U", "postgres",
                "-d", "springkb_test", "-t", "-A", "-c", sql);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        if (code != 0) {
            throw new IllegalStateException("docker exec psql 失败: " + out);
        }
        return out.trim();
    }
}