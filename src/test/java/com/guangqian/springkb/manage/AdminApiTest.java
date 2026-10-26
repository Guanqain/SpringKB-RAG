package com.guangqian.springkb.manage;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.guangqian.springkb.demo.FakeDocumentSeeder;
import com.guangqian.springkb.rag.RagSearchService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * Task 6 管理接口集成测试（springkb_test 真实库 + 确定性 mock Embedding）：
 * 鉴权 401、列表过滤/分页、单文档导入（official/community 幂等）、active 双写与检索不召回、删除、chunk 列表、参数校验。
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AdminApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    RagSearchService ragSearchService;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    EmbeddingModel embeddingModel;

    /** 挡住 demo seeder：空测试库首次启动时避免它调用未 stub 的 embedding。 */
    @MockitoBean
    FakeDocumentSeeder fakeDocumentSeeder;

    private static final int DIMENSIONS = 1024;

    @BeforeAll
    static void ensureTestDatabase() throws Exception {
        String exists = dockerPsql("SELECT 1 FROM pg_database WHERE datname='springkb_test'");
        if (!exists.contains("1")) {
            dockerPsql("CREATE DATABASE springkb_test");
        }
        // 与 RagSearchServiceTest 同一策略：清空历史 mock 向量数据 + drop HNSW 索引强制 seq scan，
        // 保证「正文即查询」的确定性命中在 topK 内一定被召回（全同向量下 HNSW 贪心遍历可能漏节点）。
        dockerPsqlDb("DELETE FROM vector_store");
        dockerPsqlDb("DELETE FROM kb_chunk");
        dockerPsqlDb("DELETE FROM kb_document");
        dockerPsqlDb("DROP INDEX IF EXISTS spring_ai_vector_index");
    }

    @BeforeEach
    void stubEmbeddingModel() {
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
        // doSimilaritySearch 生成 query 向量走单参数 embed(String)；mock 下同样需显式 stub
        when(embeddingModel.embed(anyString())).thenAnswer(inv -> vectorFor(inv.getArgument(0)));
    }

    // ---------- 1. 鉴权：未登录访问 /api/admin/* 一律 401 ----------
    @Test
    void unauthenticatedAccessRejected401() throws Exception {
        mockMvc.perform(get("/api/admin/documents"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/admin/documents/x/active").contentType(APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/documents/import"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 2. 列表：过滤（source/version/active/keyword）与分页形态 ----------
    @Test
    void listWithFiltersAndPagination() throws Exception {
        String token = registerAndLogin();
        String uid = uid();
        String docId = importViaApi(token, officialBody(uid));

        // 全量列表包含新文档，且默认不含分页包装（data 为数组）
        mockMvc.perform(get("/api/admin/documents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.id == '%s')]", docId).exists());

        // keyword 过滤（唯一 uid 保证命中自身）
        mockMvc.perform(get("/api/admin/documents").param("keyword", uid)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(docId));

        // source/version/active 组合过滤
        mockMvc.perform(get("/api/admin/documents")
                        .param("source", "official")
                        .param("version", "3.3")
                        .param("active", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // active 参数非法 → 400
        mockMvc.perform(get("/api/admin/documents").param("active", "maybe")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        // 分页形态：page+size 同时给出 → data 为 { list, total, page, size }
        mockMvc.perform(get("/api/admin/documents").param("page", "1").param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2));
    }

    // ---------- 3. 单文档导入：official 文本导入 + 幂等跳过 ----------
    @Test
    void importSingleDocumentIsIdempotent() throws Exception {
        String token = registerAndLogin();
        String uid = uid();
        String body = officialBody(uid);

        MvcResult first = mockMvc.perform(post("/api/admin/documents/import")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.imported").value(true))
                .andExpect(jsonPath("$.data.skipped").value(false))
                .andReturn();
        String docId = jsonPathText(first, "/data/docId");

        // 重复提交 → 幂等跳过
        mockMvc.perform(post("/api/admin/documents/import")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.imported").value(false))
                .andExpect(jsonPath("$.data.skipped").value(true));

        assertThat(count("SELECT count(*) FROM kb_document WHERE id = ?", docId)).isEqualTo(1);
    }

    // ---------- 4. community 导入：authority 落库为 community ----------
    @Test
    void importCommunityDocumentPersistsAuthority() throws Exception {
        String token = registerAndLogin();
        String uid = uid();
        String body = json(Map.of(
                "source", "community",
                "version", "3.3",
                "title", "SO Community Doc " + uid,
                "docKey", "SO-" + uid,
                "text", "Community answer about CDS startup in Spring Boot " + uid));

        MvcResult r = mockMvc.perform(post("/api/admin/documents/import")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(true))
                .andReturn();
        String docId = jsonPathText(r, "/data/docId");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT authority, origin FROM kb_document WHERE id = ?", docId);
        assertThat(row.get("authority")).isEqualTo("community");
        assertThat(row.get("origin")).isEqualTo("stackoverflow.com");
        assertThat(count("SELECT count(*) FROM vector_store WHERE metadata ->> 'doc_id' = ? AND metadata ->> 'authority' = 'community'", docId))
                .isGreaterThan(0);
    }

    // ---------- 5. active 双写：切换 inactive 后检索不召回，切回恢复 ----------
    @Test
    void toggleActiveExcludesDocFromRetrieval() throws Exception {
        String token = registerAndLogin();
        String uid = uid();
        // 正文与查询完全一致 → 确定性向量 → 必定命中（active 态）
        String text = "Admin toggle active retrieval probe " + uid;
        String docId = importViaApi(token, json(Map.of(
                "source", "official",
                "version", "3.3",
                "title", "Toggle Probe " + uid,
                "docKey", "toggle-probe-" + uid,
                "text", text)));

        // 初始：可召回
        assertThat(hitDocIds(text)).contains(docId);

        // PATCH inactive：接口返回 false，双写落库
        mockMvc.perform(patch("/api/admin/documents/{id}/active", docId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT active FROM kb_document WHERE id = ?", Boolean.class, docId)).isFalse();
        assertThat(count("SELECT count(*) FROM vector_store WHERE metadata ->> 'doc_id' = ? AND metadata ->> 'active' = 'false'", docId))
                .isGreaterThan(0);
        assertThat(hitDocIds(text)).doesNotContain(docId);

        // PATCH 切回 active → 恢复召回
        mockMvc.perform(patch("/api/admin/documents/{id}/active", docId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));
        assertThat(hitDocIds(text)).contains(docId);
    }

    // ---------- 6. 参数校验：缺字段 / 非法 source / community 缺正文 / SSRF 域名拦截 ----------
    @Test
    void importValidationErrors() throws Exception {
        String token = registerAndLogin();

        // 缺 title
        mockMvc.perform(post("/api/admin/documents/import")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("source", "official", "version", "3.3", "docKey", "k", "text", "t"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        // 非法 source
        mockMvc.perform(post("/api/admin/documents/import")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("source", "hacker", "version", "3.3", "title", "t", "docKey", "k", "text", "t"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        // community 缺正文
        mockMvc.perform(post("/api/admin/documents/import")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("source", "community", "version", "3.3", "title", "t", "docKey", "k"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        // URL 域名不在白名单（SSRF 拦截，不发网络请求）
        mockMvc.perform(post("/api/admin/documents/import")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("source", "official", "version", "3.3", "title", "t",
                                "docKey", "k", "url", "http://evil.example.com/page"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        // active 缺字段（body 为空对象）→ 400
        mockMvc.perform(patch("/api/admin/documents/whatever/active")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ---------- 7. 删除：文档 + chunk + 向量一并清理；不存在 → 404 ----------
    @Test
    void deleteRemovesDocChunksAndVectors() throws Exception {
        String token = registerAndLogin();
        String uid = uid();
        String docId = importViaApi(token, officialBody(uid));
        String chunkId = jdbcTemplate.queryForObject(
                "SELECT id FROM kb_chunk WHERE doc_id = ? ORDER BY ordinal LIMIT 1", String.class, docId);

        mockMvc.perform(delete("/api/admin/documents/{id}", docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(count("SELECT count(*) FROM kb_document WHERE id = ?", docId)).isZero();
        assertThat(count("SELECT count(*) FROM kb_chunk WHERE doc_id = ?", docId)).isZero();
        assertThat(count("SELECT count(*) FROM vector_store WHERE metadata ->> 'doc_id' = ?", docId)).isZero();
        assertThat(count("SELECT count(*) FROM vector_store WHERE id = ?", chunkId)).isZero();

        // 再删 → 404
        mockMvc.perform(delete("/api/admin/documents/{id}", docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ---------- 8. chunk 列表（引用溯源调试） ----------
    @Test
    void chunksEndpointReturnsOrderedChunks() throws Exception {
        String token = registerAndLogin();
        String uid = uid();
        String docId = importViaApi(token, officialBody(uid));

        mockMvc.perform(get("/api/admin/documents/{id}/chunks", docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").isString())
                .andExpect(jsonPath("$.data[0].content").isString())
                .andExpect(jsonPath("$.data[0].metadata.heading_path").exists())
                .andExpect(jsonPath("$.data[0].metadata.source").value("official"));

        // 不存在的文档 → 404
        mockMvc.perform(get("/api/admin/documents/nope/chunks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ---------- 9. active 切换目标文档不存在 → 404 ----------
    @Test
    void patchActiveNotFound() throws Exception {
        String token = registerAndLogin();
        mockMvc.perform(patch("/api/admin/documents/no-such-doc/active")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ---------- helpers ----------

    /**
     * 管理接口需要 ADMIN 角色：直接入库一个 ADMIN 再走密码登录。
     * （注册接口创建的角色取决于库内用户数，且 JWT 角色是登录快照，故不能依赖注册后再改库。）
     */
    private String registerAndLogin() throws Exception {
        String username = "admin" + uid();
        jdbcTemplate.update("""
                INSERT INTO kb_user (username, email, password_hash, role)
                VALUES (?, ?, ?, 'ADMIN')
                """, username, username + "@test.local", passwordEncoder.encode("123456"));
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return jsonPathText(r, "/data/token");
    }

    private String importViaApi(String token, String body) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/admin/documents/import")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return jsonPathText(r, "/data/docId");
    }

    private String officialBody(String uid) throws Exception {
        return json(Map.of(
                "source", "official",
                "version", "3.3",
                "title", "Admin Import Doc " + uid,
                "docKey", "admin/import/" + uid,
                "text", "Spring Boot reference guide section about web configuration " + uid
                        + " with enough words to form a single chunk for deterministic retrieval."));
    }

    /** 检索命中的 doc_id 集合（query 与文档正文一致时确定性命中）。 */
    private List<String> hitDocIds(String query) {
        return ragSearchService.search(query, null, 50).stream()
                .map(row -> (String) row.get("doc_id"))
                .toList();
    }

    private String json(Object o) throws Exception {
        return jsonMapper.writeValueAsString(o);
    }

    private String jsonPathText(MvcResult result, String path) throws Exception {
        JsonNode node = jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode v = node.at(path);
        if (v.isMissingNode() || v.isNull()) {
            throw new IllegalStateException("JSON 路径缺失: " + path + " in " + node);
        }
        return v.asText();
    }

    private int count(String sql, Object... args) {
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** 确定性 1024 维向量：按文本 hashCode 填充 ±0.5，与 IngestionServiceTest 一致。 */
    private static float[] vectorFor(String text) {
        float[] v = new float[DIMENSIONS];
        int h = text.hashCode();
        for (int i = 0; i < DIMENSIONS; i++) {
            v[i] = ((h ^ (i * 31)) & 1) == 0 ? 0.5f : -0.5f;
        }
        return v;
    }

    private static String dockerPsqlDb(String sql) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("docker", "exec", "rag-postgres", "psql", "-U", "postgres", "-d", "springkb_test", "-t", "-A", "-c", sql);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        if (code != 0) {
            throw new IllegalStateException("docker exec psql 失败: " + out);
        }
        return out.trim();
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
}
