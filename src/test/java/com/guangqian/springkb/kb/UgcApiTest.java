package com.guangqian.springkb.kb;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.guangqian.springkb.demo.FakeDocumentSeeder;
import com.guangqian.springkb.rag.RagSearchService;
import com.guangqian.springkb.service.AppSettingService;
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
import org.springframework.mock.web.MockMultipartFile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UGC 发布 / 脱敏扫描 / 审核链路集成测试（springkb_test 真实库 + 确定性 mock Embedding）：
 * <ul>
 *   <li>文件类型、10MB、空内容校验；</li>
 *   <li>团队文档先脱敏扫描（命中 422 且不落库），通过后 pending，管理员审批后才可读、可检索；</li>
 *   <li>私有文档免扫描免审，仅作者可见、永不入检索；private → team 必须重新扫描并待审；</li>
 *   <li>驳回后作者可见理由、他人不可见；作者编辑后重新提交再审核；</li>
 *   <li>存在多名管理员时不能审核自己发布的文档。</li>
 * </ul>
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class UgcApiTest {

    private static final int DIMENSIONS = 1024;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    RagSearchService ragSearchService;

    @Autowired
    AppSettingService appSettingService;

    @MockitoBean
    EmbeddingModel embeddingModel;

    @MockitoBean
    FakeDocumentSeeder fakeDocumentSeeder;

    @BeforeAll
    static void dropVectorIndex() throws Exception {
        // 全同 mock 向量下 HNSW 贪心遍历可能漏节点，drop 索引强制精确 seq scan
        dockerPsqlDb("DROP INDEX IF EXISTS spring_ai_vector_index");
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM vector_store");
        jdbcTemplate.update("DELETE FROM kb_chunk");
        jdbcTemplate.update("DELETE FROM kb_document");
        appSettingService.setAllowAnonymous(true);
        stubEmbeddingModel();
    }

    // ---------- 1. 文件校验：类型 / 大小 / 空内容 ----------
    @Test
    void uploadRejectsUnsupportedTypeOversizeAndEmptyContent() throws Exception {
        Account author = account("MEMBER");

        MockMultipartFile pdf = new MockMultipartFile("file", "guide.pdf",
                "application/pdf", "pdf-bytes".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/ugc/documents/upload").file(pdf)
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("仅支持 .txt / .md 文本文件"));

        MockMultipartFile blank = new MockMultipartFile("file", "blank.txt",
                "text/plain", "  \n  ".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/ugc/documents/upload").file(blank)
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("文件内容为空"));

        byte[] tooLarge = new byte[10 * 1024 * 1024 + 1];
        Arrays.fill(tooLarge, (byte) 'a');
        MockMultipartFile big = new MockMultipartFile("file", "big.md", "text/markdown", tooLarge);
        mockMvc.perform(multipart("/api/ugc/documents/upload").file(big)
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("文件大小不能超过 10MB"));

        assertThat(count("SELECT count(*) FROM kb_document")).isZero();
    }

    // ---------- 2. 技术分类注册 + 版本聚合只在审核通过后才可见 ----------
    @Test
    void uploadRegistersCategoryAndAggregatesVersionsOnlyAfterApproval() throws Exception {
        Account author = account("MEMBER");
        Account admin = account("ADMIN");
        String product = "自定义技术" + uid();

        MvcResult first = mockMvc.perform(multipart("/api/ugc/documents/upload")
                        .file(textFile("guide.md", "## 自定义分类文档\n\n这是团队自定义技术分类的正文。"))
                        .param("title", "自定义分类文档")
                        .param("product", product)
                        .param("version", "v1.0")
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reviewStatus").value("pending"))
                .andReturn();
        String docId = readPath(first, "/data/docId").asText();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT product, version, author, visibility, review_status FROM kb_document WHERE id = ?",
                docId);
        assertThat(row.get("product")).isEqualTo(product);
        assertThat(row.get("version")).isEqualTo("v1.0");
        assertThat(row.get("author")).isEqualTo(author.username());
        assertThat(row.get("visibility")).isEqualTo("team");
        assertThat(row.get("review_status")).isEqualTo("pending");

        // 待审文档不进分类的版本聚合（避免未过审内容提前暴露）
        JsonNode pendingCategory = findCategory(readPath(mockMvc.perform(get("/api/kb/categories"))
                .andExpect(status().isOk())
                .andReturn(), "/data"), product);
        assertThat(pendingCategory).isNotNull();
        assertThat(stringList(pendingCategory.get("versions"))).doesNotContain("v1.0");

        approve(docId, admin.token());

        JsonNode category = findCategory(readPath(mockMvc.perform(get("/api/kb/categories"))
                .andExpect(status().isOk())
                .andReturn(), "/data"), product);
        assertThat(category).isNotNull();
        assertThat(stringList(category.get("versions"))).contains("v1.0");

        // 同名分类下再传一篇不填版本 → 通过审核后版本展示「未标注」
        MvcResult second = mockMvc.perform(multipart("/api/ugc/documents/upload")
                        .file(textFile("notes.txt", "未标注版本的团队笔记正文。"))
                        .param("product", product)
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        approve(readPath(second, "/data/docId").asText(), admin.token());
        JsonNode after = findCategory(readPath(mockMvc.perform(get("/api/kb/categories"))
                .andExpect(status().isOk())
                .andReturn(), "/data"), product);
        assertThat(after).isNotNull();
        assertThat(stringList(after.get("versions"))).contains("v1.0", "未标注");

        // 不填分类 → 未分类
        MvcResult misc = mockMvc.perform(multipart("/api/ugc/documents/upload")
                        .file(textFile("misc.txt", "没有指定技术分类的零散笔记。"))
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT product FROM kb_document WHERE id = ?", String.class,
                readPath(misc, "/data/docId").asText())).isEqualTo(UgcService.UNCATEGORIZED_PRODUCT);
    }

    // ---------- 3. private：仅作者可见、不进检索；切换 team 后需审核 ----------
    @Test
    void privateDocumentIsAuthorOnlyAndNeverCitedUntilApproved() throws Exception {
        Account author = account("MEMBER");
        Account other = account("MEMBER");
        Account admin = account("ADMIN");
        String text = "Private CDS secret note " + uid();

        MvcResult uploaded = mockMvc.perform(multipart("/api/ugc/documents/upload")
                        .file(textFile("secret.md", text))
                        .param("title", "私有笔记")
                        .param("visibility", "private")
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reviewStatus").value("approved"))
                .andReturn();
        String docId = readPath(uploaded, "/data/docId").asText();

        // 匿名 / 他人 → 404；作者 → 可见且为 private
        mockMvc.perform(get("/api/kb/doc/{id}", docId))
                .andExpect(jsonPath("$.code").value(404));
        mockMvc.perform(get("/api/kb/doc/{id}", docId).header("Authorization", "Bearer " + other.token()))
                .andExpect(jsonPath("$.code").value(404));
        mockMvc.perform(get("/api/kb/doc/{id}", docId).header("Authorization", "Bearer " + author.token()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.visibility").value("private"));

        // 检索层强制过滤：private 文档（即使正文与 query 完全一致）不出现
        assertThat(hitDocIds(text)).doesNotContain(docId);

        // 非作者不能切换可见性
        mockMvc.perform(patch("/api/ugc/documents/{id}/visibility", docId)
                        .header("Authorization", "Bearer " + other.token())
                        .contentType(APPLICATION_JSON).content("{\"visibility\":\"team\"}"))
                .andExpect(jsonPath("$.code").value(400));

        // 作者切换为 team → 进入待审：匿名不可读、不参与检索
        mockMvc.perform(patch("/api/ugc/documents/{id}/visibility", docId)
                        .header("Authorization", "Bearer " + author.token())
                        .contentType(APPLICATION_JSON).content("{\"visibility\":\"team\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("pending"));
        Map<String, Object> state = jdbcTemplate.queryForMap(
                "SELECT visibility, review_status FROM kb_document WHERE id = ?", docId);
        assertThat(state.get("visibility")).isEqualTo("team");
        assertThat(state.get("review_status")).isEqualTo("pending");
        mockMvc.perform(get("/api/kb/doc/{id}", docId))
                .andExpect(jsonPath("$.code").value(404));
        assertThat(hitDocIds(text)).doesNotContain(docId);

        // 管理员通过后才匿名可读、进入检索
        approve(docId, admin.token());
        mockMvc.perform(get("/api/kb/doc/{id}", docId))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(hitDocIds(text)).contains(docId);
    }

    // ---------- 4. 脱敏扫描：团队文档命中即拦截且不落库；私有文档免扫描 ----------
    @Test
    void sensitiveContentBlocksTeamPublishButPrivateBypassesScan() throws Exception {
        Account author = account("MEMBER");
        String secret = "AKIAIOSFODNN7EXAMPLE";
        String markdown = "部署前请检查配置：\n\naws_access_key_id = " + secret;
        Map<String, Object> payload = Map.of("title", "含密钥的笔记", "markdown", markdown);

        // 预检接口同样拦截
        mockMvc.perform(post("/api/ugc/scan").contentType(APPLICATION_JSON)
                        .content(json(payload)).header("Authorization", "Bearer " + author.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.blocked").value(true))
                .andExpect(jsonPath("$.data.findings[0].type").value("cloud_key"));

        // 团队发布 → 422 + 命中项（样例已打码），且不落库
        MvcResult blocked = mockMvc.perform(post("/api/ugc/documents").contentType(APPLICATION_JSON)
                        .content(json(payload)).header("Authorization", "Bearer " + author.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(422))
                .andExpect(jsonPath("$.message").value("文档包含敏感信息，请修改后再发布"))
                .andExpect(jsonPath("$.data.blocked").value(true))
                .andExpect(jsonPath("$.data.findings[0].type").value("cloud_key"))
                .andExpect(jsonPath("$.data.findings[0].count").value(1))
                .andReturn();
        String body = blocked.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain(secret);
        assertThat(count("SELECT count(*) FROM kb_document")).isZero();

        // 私有文档免扫描：同样的正文可以直接发布，仅作者可见
        MvcResult privatePublish = mockMvc.perform(post("/api/ugc/documents").contentType(APPLICATION_JSON)
                        .content(json(Map.of("title", "含密钥的笔记", "markdown", markdown,
                                "visibility", "private")))
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reviewStatus").value("approved"))
                .andExpect(jsonPath("$.data.visibility").value("private"))
                .andReturn();
        String docId = readPath(privatePublish, "/data/docId").asText();
        mockMvc.perform(get("/api/kb/doc/{id}", docId).header("Authorization", "Bearer " + author.token()))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(hitDocIds(secret)).doesNotContain(docId);
    }

    // ---------- 5. 驳回 → 作者可见理由；编辑重提 → 再审核 → 通过后可读可检索 ----------
    @Test
    void rejectionKeepsDocAuthorOnlyAndEditResubmitsForReview() throws Exception {
        Account author = account("MEMBER");
        Account other = account("MEMBER");
        Account admin = account("ADMIN");
        String original = "待审文档正文 " + uid();

        MvcResult published = mockMvc.perform(post("/api/ugc/documents").contentType(APPLICATION_JSON)
                        .content(json(Map.of("title", "待审文档", "markdown", original)))
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reviewStatus").value("pending"))
                .andReturn();
        String docId = readPath(published, "/data/docId").asText();

        // 待审：匿名 / 他人不可见，作者可见；不进检索
        mockMvc.perform(get("/api/kb/doc/{id}", docId))
                .andExpect(jsonPath("$.code").value(404));
        mockMvc.perform(get("/api/kb/doc/{id}", docId).header("Authorization", "Bearer " + other.token()))
                .andExpect(jsonPath("$.code").value(404));
        mockMvc.perform(get("/api/kb/doc/{id}", docId).header("Authorization", "Bearer " + author.token()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reviewStatus").value("pending"));
        assertThat(hitDocIds(original)).doesNotContain(docId);

        // 审核队列可见该文档
        mockMvc.perform(get("/api/admin/reviews").header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(docId))
                .andExpect(jsonPath("$.data[0].author").value(author.username()));

        // 驳回必须写理由
        mockMvc.perform(post("/api/admin/reviews/{id}/reject", docId).contentType(APPLICATION_JSON)
                        .content("{\"note\":\"\"}").header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(post("/api/admin/reviews/{id}/reject", docId).contentType(APPLICATION_JSON)
                        .content(json(Map.of("note", "请补充版本说明")))
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.code").value(0));

        // 驳回后：作者可见理由，他人 / 匿名仍 404，不参与检索
        mockMvc.perform(get("/api/kb/doc/{id}", docId).header("Authorization", "Bearer " + author.token()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reviewStatus").value("rejected"))
                .andExpect(jsonPath("$.data.reviewNote").value("请补充版本说明"));
        mockMvc.perform(get("/api/kb/doc/{id}", docId))
                .andExpect(jsonPath("$.code").value(404));
        assertThat(hitDocIds(original)).doesNotContain(docId);

        // 作者编辑并重新提交：旧文档替换为新文档，重新进入待审
        String revised = "补充版本说明后的正文 " + uid();
        MvcResult edited = mockMvc.perform(put("/api/ugc/documents/{id}", docId).contentType(APPLICATION_JSON)
                        .content(json(Map.of("title", "待审文档（修订）", "markdown", revised)))
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reviewStatus").value("pending"))
                .andReturn();
        String newDocId = readPath(edited, "/data/docId").asText();
        assertThat(newDocId).isNotEqualTo(docId);
        mockMvc.perform(get("/api/kb/doc/{id}", docId))
                .andExpect(jsonPath("$.code").value(404));
        assertThat(hitDocIds(revised)).doesNotContain(newDocId);

        // 通过审核 → 匿名可读、进入检索
        approve(newDocId, admin.token());
        mockMvc.perform(get("/api/kb/doc/{id}", newDocId))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(hitDocIds(revised)).contains(newDocId);
    }

    // ---------- 6. 自审约束：存在多名管理员时不能审核自己发布的文档 ----------
    @Test
    void adminCannotApproveOwnDocumentWhenMultipleAdminsExist() throws Exception {
        Account adminA = account("ADMIN");
        Account adminB = account("ADMIN");

        MvcResult published = mockMvc.perform(post("/api/ugc/documents").contentType(APPLICATION_JSON)
                        .content(json(Map.of("title", "管理员发布的团队文档", "markdown", "管理员团队正文 " + uid())))
                        .header("Authorization", "Bearer " + adminA.token()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reviewStatus").value("pending"))
                .andReturn();
        String docId = readPath(published, "/data/docId").asText();

        mockMvc.perform(post("/api/admin/reviews/{id}/approve", docId).contentType(APPLICATION_JSON)
                        .content("{}").header("Authorization", "Bearer " + adminA.token()))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("存在其他管理员时不能审核自己发布的文档"));

        mockMvc.perform(post("/api/admin/reviews/{id}/approve", docId).contentType(APPLICATION_JSON)
                        .content("{}").header("Authorization", "Bearer " + adminB.token()))
                .andExpect(jsonPath("$.code").value(0));
    }

    // ---------- helpers ----------

    private void approve(String docId, String adminToken) throws Exception {
        mockMvc.perform(post("/api/admin/reviews/{id}/approve", docId).contentType(APPLICATION_JSON)
                        .content("{}").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void stubEmbeddingModel() {
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenAnswer(inv -> {
            EmbeddingRequest req = inv.getArgument(0);
            List<Embedding> embeddings = req.getInstructions().stream()
                    .map(t -> new Embedding(vectorFor(t), 0))
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
    }

    private MockMultipartFile textFile(String filename, String content) {
        return new MockMultipartFile("file", filename, "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private List<String> hitDocIds(String query) {
        return ragSearchService.search(query, null, 100).stream()
                .map(row -> (String) row.get("doc_id"))
                .toList();
    }

    private Account account(String role) throws Exception {
        String username = role.toLowerCase() + uid();
        String email = username + "@test.local";
        jdbcTemplate.update("""
                INSERT INTO kb_user (username, email, password_hash, role)
                VALUES (?, ?, ?, ?)
                """, username, email, passwordEncoder.encode("123456"), role);
        MvcResult r = mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("username", username, "password", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return new Account(username, readPath(r, "/data/token").asText());
    }

    private String json(Object o) {
        return jsonMapper.writeValueAsString(o);
    }

    private static JsonNode findCategory(JsonNode categories, String product) {
        for (JsonNode node : categories) {
            if (product.equals(node.path("product").asText())) {
                return node;
            }
        }
        return null;
    }

    private static List<String> stringList(JsonNode array) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }

    private JsonNode readPath(MvcResult result, String path) throws Exception {
        JsonNode node = jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode v = node.at(path);
        if (v.isMissingNode() || v.isNull()) {
            throw new IllegalStateException("JSON 路径缺失: " + path + " in " + node);
        }
        return v;
    }

    private int count(String sql, Object... args) {
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** 全同号向量：任意 query 与文档余弦相似度 ≈ 1，不会被 threshold=0.0 过滤，可见性/审核过滤才是唯一变量。 */
    private static float[] vectorFor(String text) {
        float[] v = new float[DIMENSIONS];
        Arrays.fill(v, 0.5f);
        return v;
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

    private record Account(String username, String token) {
    }
}
