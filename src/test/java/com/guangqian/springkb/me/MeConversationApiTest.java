package com.guangqian.springkb.me;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.guangqian.springkb.demo.FakeDocumentSeeder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 个人中心与会话管理集成测试（springkb_test 真实库 + 确定性 mock Embedding）：
 * UGC 归属、点赞/收藏幂等与计数、点赞状态读回、会话窗口裁剪、TTL 清理、跨用户隔离、鉴权 401。
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MeConversationApiTest {

    private static final int DIMENSIONS = 1024;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    JsonMapper jsonMapper;

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
    }

    @BeforeEach
    void stubEmbeddingAndClear() {
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenAnswer(inv -> {
            EmbeddingRequest req = inv.getArgument(0);
            List<Embedding> embeddings = req.getInstructions().stream()
                    .map(text -> new Embedding(vectorFor(text), 0))
                    .toList();
            return new EmbeddingResponse(embeddings);
        });
        when(embeddingModel.embed(anyList(), any(EmbeddingOptions.class), any(BatchingStrategy.class)))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    List<Document> docs = (List<Document>) inv.getArgument(0);
                    return docs.stream().map(d -> vectorFor(d.getText())).toList();
                });
        when(embeddingModel.embed(anyString())).thenAnswer(inv -> vectorFor(inv.getArgument(0)));

        jdbcTemplate.update("DELETE FROM kb_message");
        jdbcTemplate.update("DELETE FROM kb_conversation");
        jdbcTemplate.update("DELETE FROM kb_doc_like");
        jdbcTemplate.update("DELETE FROM kb_doc_favorite");
        jdbcTemplate.update("DELETE FROM vector_store");
        jdbcTemplate.update("DELETE FROM kb_chunk");
        jdbcTemplate.update("DELETE FROM kb_document");
    }

    // ---------- 1. 鉴权：未登录访问 /api/me/** 一律 401 ----------
    @Test
    void unauthenticatedMeEndpointsRejected401() throws Exception {
        mockMvc.perform(get("/api/me/documents")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/conversations")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/me/docs/x/like")).andExpect(status().isUnauthorized());
    }

    // ---------- 2. UGC 发布后绑定作者，且只出现在作者「我的文档」 ----------
    @Test
    void ugcDocumentBindsAuthorAndAppearsInMyDocuments() throws Exception {
        String tokenA = registerAndLogin();
        String tokenB = registerAndLogin();
        String title = "团队指南 " + uid();

        MvcResult r = mockMvc.perform(post("/api/ugc/documents")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("title", title,
                                "markdown", "## 团队约定\n\n这是一篇用于验证作者归属的团队文档。"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String docId = jsonPathText(r, "/data/docId");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT author FROM kb_document WHERE id = ?", String.class, docId)).isNotBlank();

        mockMvc.perform(get("/api/me/documents").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '%s')]", docId).exists())
                .andExpect(jsonPath("$.data[0].author").isString());

        // 另一个用户看不到别人写的文档
        mockMvc.perform(get("/api/me/documents").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '%s')]", docId).doesNotExist());
    }

    // ---------- 3. 点赞/收藏：幂等计数、按用户隔离、详情状态读回 ----------
    @Test
    void likeAndFavoriteAreIdempotentAndPerUser() throws Exception {
        String tokenA = registerAndLogin();
        String tokenB = registerAndLogin();
        String docId = seedDoc();

        // A 点赞两次 → 仍为 1
        mockMvc.perform(post("/api/me/docs/{id}/like", docId).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.liked").value(true));
        mockMvc.perform(post("/api/me/docs/{id}/like", docId).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.likeCount").value(1));

        // B 点赞 → 2；A 取消 → 1
        mockMvc.perform(post("/api/me/docs/{id}/like", docId).header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.data.likeCount").value(2));
        mockMvc.perform(delete("/api/me/docs/{id}/like", docId).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.liked").value(false));

        // 收藏 + 我的收藏列表
        mockMvc.perform(post("/api/me/docs/{id}/favorite", docId).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.favorited").value(true))
                .andExpect(jsonPath("$.data.favoriteCount").value(1));
        mockMvc.perform(get("/api/me/favorites").header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data[?(@.id == '%s')]", docId).exists());
        // B 未收藏
        mockMvc.perform(get("/api/me/favorites").header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.data.length()").value(0));

        // 文档详情：A 看到自己的收藏状态与公开计数；匿名只看到计数
        mockMvc.perform(get("/api/kb/doc/{id}", docId).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.favoriteCount").value(1))
                .andExpect(jsonPath("$.data.favorited").value(true))
                .andExpect(jsonPath("$.data.liked").value(false));
        mockMvc.perform(get("/api/kb/doc/{id}", docId))
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.favorited").value(false));

        // 不存在的文档 → 404
        mockMvc.perform(post("/api/me/docs/no-such-doc/like").header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.code").value(404));
    }

    // ---------- 4. 会话：窗口裁剪到 10 轮、重命名、跨用户隔离、TTL 清理、删除 ----------
    @Test
    void conversationWindowTrimRenameIsolationTtlAndDelete() throws Exception {
        String token = registerAndLogin();
        String other = registerAndLogin();

        MvcResult created = mockMvc.perform(post("/api/me/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("title", "窗口测试"))))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.messages.length()").value(0))
                .andReturn();
        long convId = readPath(created, "/data/id").asLong();

        // 追加 12 轮：只保留最近 10 轮
        for (int i = 1; i <= 12; i++) {
            MvcResult r = mockMvc.perform(post("/api/me/conversations/{id}/messages", convId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(APPLICATION_JSON)
                            .content(json(Map.of(
                                    "question", "q" + i,
                                    "answer", "a" + i,
                                    "found", true,
                                    "sources", List.of()))))
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();
            int expected = Math.min(i, 10);
            assertThat(readPath(r, "/data/messages").size()).isEqualTo(expected);
        }

        mockMvc.perform(get("/api/me/conversations").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data[0].id").value(convId))
                .andExpect(jsonPath("$.data[0].messageCount").value(10));

        // 最早保留的是 q3（q1/q2 被裁掉）
        MvcResult detail = mockMvc.perform(get("/api/me/conversations/{id}", convId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.messages.length()").value(10))
                .andReturn();
        assertThat(readPath(detail, "/data/messages/0/question").asText()).isEqualTo("q3");

        // 重命名
        mockMvc.perform(patch("/api/me/conversations/{id}", convId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("title", "改名后的会话"))))
                .andExpect(jsonPath("$.data.title").value("改名后的会话"));

        // 跨用户隔离：另一用户读/删都 404
        mockMvc.perform(get("/api/me/conversations/{id}", convId).header("Authorization", "Bearer " + other))
                .andExpect(jsonPath("$.code").value(404));
        mockMvc.perform(delete("/api/me/conversations/{id}", convId).header("Authorization", "Bearer " + other))
                .andExpect(jsonPath("$.code").value(404));

        // TTL：把会话更新时间改到 40 天前 → 列表时被清理
        jdbcTemplate.update("UPDATE kb_conversation SET updated_at = now() - INTERVAL '40 days' WHERE id = ?", convId);
        mockMvc.perform(get("/api/me/conversations").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.length()").value(0));
        assertThat(count("SELECT count(*) FROM kb_message WHERE conversation_id = ?", convId)).isZero();

        // 删除：新建后删除 → 列表为空
        MvcResult again = mockMvc.perform(post("/api/me/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content(json(Map.of("title", "待删除"))))
                .andReturn();
        long deleteId = readPath(again, "/data/id").asLong();
        mockMvc.perform(delete("/api/me/conversations/{id}", deleteId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/me/conversations").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ---------- helpers ----------

    private String seedDoc() {
        String id = "me-doc-" + uid();
        jdbcTemplate.update("""
                INSERT INTO kb_document (id, product, title, origin, authority, version, doc_key,
                                         doc_type, language, active)
                VALUES (?, 'spring-boot', '点赞收藏测试文档', 'spring.io', 'official', '4.2-SNAPSHOT', ?,
                        'reference', 'en', true)
                """, id, "me/" + uid());
        return id;
    }

    private String registerAndLogin() throws Exception {
        String username = "user" + uid();
        String body = json(Map.of("username", username, "password", "123456"));
        MvcResult r = mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return jsonPathText(r, "/data/token");
    }

    private String json(Object o) {
        return jsonMapper.writeValueAsString(o);
    }

    private JsonNode readPath(MvcResult result, String path) throws Exception {
        JsonNode node = jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode v = node.at(path);
        if (v.isMissingNode() || v.isNull()) {
            throw new IllegalStateException("JSON 路径缺失: " + path + " in " + node);
        }
        return v;
    }

    private String jsonPathText(MvcResult result, String path) throws Exception {
        return readPath(result, path).asText();
    }

    private int count(String sql, Object... args) {
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static float[] vectorFor(String text) {
        float[] v = new float[DIMENSIONS];
        int h = text.hashCode();
        for (int i = 0; i < DIMENSIONS; i++) {
            v[i] = ((h ^ (i * 31)) & 1) == 0 ? 0.5f : -0.5f;
        }
        return v;
    }

    private static String dockerPsql(String sql) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("docker", "exec", "rag-postgres", "psql", "-U", "postgres", "-t", "-A", "-c", sql);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) {
            throw new IllegalStateException("docker exec psql 失败: " + out);
        }
        return out.trim();
    }
}
