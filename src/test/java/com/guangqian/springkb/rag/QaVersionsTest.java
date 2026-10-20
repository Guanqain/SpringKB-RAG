package com.guangqian.springkb.rag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.guangqian.springkb.demo.FakeDocumentSeeder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/qa/versions（用户端版本下拉元数据）集成测试（springkb_test 真实库）：
 * 1. 公开可访问——未携带 token 返回 200（SecurityConfig /api/qa/** permitAll 覆盖新端点）；
 * 2. 仅返回 active 文档版本、去重且升序（inactive 不泄漏）。
 * 测试不触发向量写入，无需 stub EmbeddingModel。
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class QaVersionsTest {

    private static final String ID_PREFIX = "qa-versions-";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    JsonMapper jsonMapper;

    /** 挡住 demo seeder：避免空库首启时触发未 stub 的 embedding 调用。 */
    @MockitoBean
    FakeDocumentSeeder fakeDocumentSeeder;

    @BeforeAll
    static void ensureTestDatabase() throws Exception {
        String exists = dockerPsql("SELECT 1 FROM pg_database WHERE datname='springkb_test'");
        if (!exists.contains("1")) {
            dockerPsql("CREATE DATABASE springkb_test");
        }
    }

    @AfterAll
    static void cleanup() throws Exception {
        dockerPsqlDb("DELETE FROM kb_document WHERE id LIKE '" + ID_PREFIX + "%'");
    }

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM kb_document WHERE id LIKE ?", ID_PREFIX + "%");
    }

    // ---------- 1. 公开可访问（无 token 200） ----------
    @Test
    void versionsEndpointIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/api/qa/versions"))
                .andExpect(status().isOk());
    }

    // ---------- 2. 仅 active、去重、升序（inactive 不泄漏） ----------
    @Test
    void listsOnlyActiveVersionsDedupedAndSorted() throws Exception {
        insertDoc("a", "3.x", true);
        insertDoc("b", "3.x", true);
        insertDoc("c", "latest", true);
        insertDoc("d", "9.9-internal-draft", false);

        MvcResult result = mockMvc.perform(get("/api/qa/versions"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(node.get("code").asInt()).isZero();
        JsonNode data = node.get("data");
        assertThat(data.isArray()).isTrue();

        List<String> versions = new ArrayList<>();
        data.forEach(v -> versions.add(v.asText()));
        // 共享测试库可能留有其他测试类的文档：断言用相对形式
        assertThat(versions).contains("3.x", "latest").doesNotContain("9.9-internal-draft");
        assertThat(versions).doesNotHaveDuplicates();
        assertThat(versions).isSorted();
    }

    // ---------- helpers ----------

    private void insertDoc(String suffix, String version, boolean active) {
        jdbcTemplate.update("""
                INSERT INTO kb_document (id, product, title, origin, authority, version, doc_key, active)
                VALUES (?, 'vue', '版本元数据测试文档', 'vuejs.org', 'official', ?, ?, ?)
                """,
                ID_PREFIX + suffix, version, "doc-key-" + suffix, active);
    }

    private static String dockerPsql(String sql) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("docker", "exec", "rag-postgres", "psql", "-U", "postgres",
                "-t", "-A", "-c", sql);
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
