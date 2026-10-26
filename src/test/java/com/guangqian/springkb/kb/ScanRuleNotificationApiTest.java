package com.guangqian.springkb.kb;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.guangqian.springkb.demo.FakeDocumentSeeder;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 本轮四项补充能力的集成测试（springkb_test 真实库 + 确定性 mock Embedding）：
 * <ul>
 *   <li>脱敏规则可配置：关闭内置规则后不再拦截；自定义规则命中；非法正则 400；内置规则不可删除；</li>
 *   <li>拦截留痕：命中写入 kb_scan_log（只含规则类型 / 命中数量 / 操作者 / 标题）；</li>
 *   <li>审核结果通知：通过 / 驳回写入站内通知，未读数与已读操作正确；</li>
 *   <li>审核队列筛选与批量通过：按技术分类 / 作者 / 关键词过滤，批量通过失败项不中断。</li>
 * </ul>
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ScanRuleNotificationApiTest {

    private static final int DIMENSIONS = 1024;

    /** 会被内置 phone 规则命中的正文。 */
    private static final String PHONE_TEXT = "联系人电话：13800138000，请勿外传。";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ScanRuleService scanRuleService;

    @MockitoBean
    EmbeddingModel embeddingModel;

    /** 挡住 demo seeder：空测试库首次启动时避免它调用未 stub 的 embedding。 */
    @MockitoBean
    FakeDocumentSeeder fakeDocumentSeeder;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM kb_notification");
        jdbcTemplate.update("DELETE FROM kb_scan_log");
        jdbcTemplate.update("DELETE FROM vector_store");
        jdbcTemplate.update("DELETE FROM kb_chunk");
        jdbcTemplate.update("DELETE FROM kb_document");
        // 规则表是全局状态：每个用例前恢复「内置全开、自定义清空」
        jdbcTemplate.update("DELETE FROM kb_scan_rule WHERE builtin = false");
        scanRuleService.seedBuiltinRules();
        jdbcTemplate.update("UPDATE kb_scan_rule SET enabled = true WHERE builtin = true");
        stubEmbeddingModel();
    }

    // ---------- 1. 规则开关 + 拦截留痕 ----------
    @Test
    void disablingBuiltinRuleStopsBlockingAndAuditKeepsOnlyMetadata() throws Exception {
        Account author = account("MEMBER");
        Account admin = account("ADMIN");

        // 命中 phone 规则 → 拦截且不落库
        mockMvc.perform(post("/api/ugc/documents").contentType(APPLICATION_JSON)
                        .content(json(Map.of("title", "含手机号的团队文档", "markdown", PHONE_TEXT)))
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(422))
                .andExpect(jsonPath("$.data.findings[0].type").value("phone"));
        assertThat(count("SELECT count(*) FROM kb_document")).isZero();

        // 留痕：操作者 / 动作 / 标题 / 规则类型 / 命中数量，且不含正文与命中值
        Map<String, Object> logRow = jdbcTemplate.queryForMap(
                "SELECT username, action, doc_title, rule_type, hit_count FROM kb_scan_log");
        assertThat(logRow.get("username")).isEqualTo(author.username());
        assertThat(logRow.get("action")).isEqualTo("publish");
        assertThat(logRow.get("doc_title")).isEqualTo("含手机号的团队文档");
        assertThat(logRow.get("rule_type")).isEqualTo("phone");
        assertThat(logRow.get("hit_count")).isEqualTo(1);

        mockMvc.perform(get("/api/admin/scan-logs").param("limit", "10")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].ruleType").value("phone"))
                .andExpect(jsonPath("$.data[0].hitCount").value(1))
                .andExpect(jsonPath("$.data[0].username").value(author.username()));

        // 关闭 phone 规则 → 同一正文可以发布（进入待审）
        mockMvc.perform(patch("/api/admin/scan-rules/{code}", "phone").contentType(APPLICATION_JSON)
                        .content("{\"enabled\":false}")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(post("/api/ugc/documents").contentType(APPLICATION_JSON)
                        .content(json(Map.of("title", "含手机号的团队文档", "markdown", PHONE_TEXT)))
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reviewStatus").value("pending"));
        // 成功发布不会新增拦截留痕
        assertThat(count("SELECT count(*) FROM kb_scan_log")).isEqualTo(1);
    }

    // ---------- 2. 自定义规则：新增 / 命中 / 非法正则 / 删除内置 ----------
    @Test
    void customRuleTakesEffectAndInvalidPatternIsRejected() throws Exception {
        Account author = account("MEMBER");
        Account admin = account("ADMIN");

        // 非法正则 → 400
        mockMvc.perform(post("/api/admin/scan-rules").contentType(APPLICATION_JSON)
                        .content(json(Map.of("label", "坏规则", "pattern", "([a-z")))
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.code").value(400));

        // 新增工号规则
        MvcResult created = mockMvc.perform(post("/api/admin/scan-rules").contentType(APPLICATION_JSON)
                        .content(json(Map.of("label", "工号", "pattern", "EMP\\d{6}")))
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.builtin").value(false))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andReturn();
        JsonNode rule = readPath(created, "/data");
        String code = rule.get("code").asText();
        assertThat(code).startsWith("custom_");

        // 预检命中自定义规则
        mockMvc.perform(post("/api/ugc/scan").contentType(APPLICATION_JSON)
                        .content(json(Map.of("title", "员工信息", "markdown", "员工号 EMP123456 请归档")))
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.blocked").value(true))
                .andExpect(jsonPath("$.data.findings[0].type").value(code));

        // 内置规则不可删除，只能关闭
        mockMvc.perform(delete("/api/admin/scan-rules/{code}", "phone")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("内置规则不可删除，可改为关闭"));

        // 删除自定义规则后不再命中
        mockMvc.perform(delete("/api/admin/scan-rules/{code}", code)
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/ugc/scan").contentType(APPLICATION_JSON)
                        .content(json(Map.of("title", "员工信息", "markdown", "员工号 EMP123456 请归档")))
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.blocked").value(false));
    }

    // ---------- 3. 审核结果通知：驳回 / 通过 各写一条，未读数与已读 ----------
    @Test
    void reviewDecisionsWriteNotificationsAndUnreadCountTracksReadState() throws Exception {
        Account author = account("MEMBER");
        Account admin = account("ADMIN");

        MvcResult published = mockMvc.perform(post("/api/ugc/documents").contentType(APPLICATION_JSON)
                        .content(json(Map.of("title", "需要审核的文档", "markdown", "事务传播机制说明 " + uid())))
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String docId = readPath(published, "/data/docId").asText();

        // 驳回 → 通知作者，理由即 body
        mockMvc.perform(post("/api/admin/reviews/{id}/reject", docId).contentType(APPLICATION_JSON)
                        .content(json(Map.of("note", "请补充版本说明")))
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/me/notifications")
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.unread").value(1))
                .andExpect(jsonPath("$.data.items[0].kind").value("review_rejected"))
                .andExpect(jsonPath("$.data.items[0].body").value("请补充版本说明"))
                .andExpect(jsonPath("$.data.items[0].read").value(false))
                .andExpect(jsonPath("$.data.items[0].docId").value(docId));

        // 编辑重提 → 通过 → 第二条通知
        MvcResult resubmitted = mockMvc.perform(put("/api/ugc/documents/{id}", docId)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("title", "需要审核的文档（修订）", "markdown", "补充版本说明 " + uid())))
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String newDocId = readPath(resubmitted, "/data/docId").asText();
        mockMvc.perform(post("/api/admin/reviews/{id}/approve", newDocId).contentType(APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/me/notifications")
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(jsonPath("$.data.unread").value(2))
                .andExpect(jsonPath("$.data.items[0].kind").value("review_approved"))
                .andExpect(jsonPath("$.data.items[1].kind").value("review_rejected"));

        // 单条已读 → 未读数下降
        long firstId = readPath(mockMvc.perform(get("/api/me/notifications")
                        .header("Authorization", "Bearer " + author.token()))
                .andReturn(), "/data/items/0/id").asLong();
        mockMvc.perform(post("/api/me/notifications/{id}/read", firstId)
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/me/notifications")
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(jsonPath("$.data.unread").value(1));

        // 全部已读 → 未读归零
        mockMvc.perform(post("/api/me/notifications/read-all")
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(1));
        mockMvc.perform(get("/api/me/notifications")
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(jsonPath("$.data.unread").value(0));
    }

    // ---------- 4. 审核队列筛选 + 批量通过（失败不中断） ----------
    @Test
    void reviewQueueFiltersAndBatchApproveReportsPerDocFailures() throws Exception {
        Account authorA = account("MEMBER");
        Account authorB = account("MEMBER");
        Account admin = account("ADMIN");
        String productA = "Java" + uid();
        String productB = "Go" + uid();
        String token = "批量测试" + uid();

        String doc1 = publish(authorA, token + " 一号", productA);
        String doc2 = publish(authorA, token + " 二号", productA);
        String doc3 = publish(authorA, token + " 三号", productB);
        String doc4 = publish(authorB, token + " 四号", productA);

        // 技术分类筛选
        mockMvc.perform(get("/api/admin/reviews").param("product", productA)
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(3));
        // 作者筛选
        mockMvc.perform(get("/api/admin/reviews").param("author", authorA.username())
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.data.length()").value(3));
        // 关键词筛选（标题）
        mockMvc.perform(get("/api/admin/reviews").param("keyword", token + " 一号")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(doc1));
        // 组合筛选：product + author
        mockMvc.perform(get("/api/admin/reviews").param("product", productB)
                        .param("author", authorA.username())
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(doc3));

        // 先单独通过 doc3，使其在批量时成为失败项
        mockMvc.perform(post("/api/admin/reviews/{id}/approve", doc3).contentType(APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/admin/reviews/batch-approve").contentType(APPLICATION_JSON)
                        .content(json(Map.of("ids", List.of(doc1, doc2, doc3, doc4))))
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.approved").value(3))
                .andExpect(jsonPath("$.data.failed").value(1))
                .andExpect(jsonPath("$.data.errors[0]").value(org.hamcrest.Matchers.containsString(doc3)));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT review_status FROM kb_document WHERE id = ?", String.class, doc1))
                .isEqualTo("approved");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT review_status FROM kb_document WHERE id = ?", String.class, doc4))
                .isEqualTo("approved");
        // 批量通过同样写通知（authorA 三篇：doc1/doc2 批量 + doc3 单条）
        assertThat(count("SELECT count(*) FROM kb_notification WHERE username = ?", authorA.username()))
                .isEqualTo(3);
        assertThat(count("SELECT count(*) FROM kb_notification WHERE username = ?", authorB.username()))
                .isEqualTo(1);

        // 空选择 → 400
        mockMvc.perform(post("/api/admin/reviews/batch-approve").contentType(APPLICATION_JSON)
                        .content("{\"ids\":[]}")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ---------- helpers ----------

    private String publish(Account author, String title, String product) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ugc/documents").contentType(APPLICATION_JSON)
                        .content(json(Map.of("title", title, "markdown", title + " 的正文 " + uid(),
                                "product", product)))
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return readPath(result, "/data/docId").asText();
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

    /** 全同号向量：避免相似度阈值影响本用例关注的规则/审核逻辑。 */
    private static float[] vectorFor(String text) {
        float[] v = new float[DIMENSIONS];
        Arrays.fill(v, 0.5f);
        return v;
    }

    private record Account(String username, String token) {
    }
}
