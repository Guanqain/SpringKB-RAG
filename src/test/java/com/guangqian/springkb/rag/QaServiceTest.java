package com.guangqian.springkb.rag;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.guangqian.springkb.demo.FakeDocumentSeeder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 问答服务集成测试（Task 4 能力三）：真实 PostgreSQL（springkb_test），EmbeddingModel 用
 * 可预测相似度的确定性 mock（含 "config"/"cds" 关键字 → 近同向量 V_config，其余 → 正交向量
 * V_other），ChatClient 用 @MockitoBean mock（链式 stub prompt→call→content）。
 *
 * <p>向量设计：V_config 为全 +0.5 再按文本 hash 扰动 16 维（query 与 chunk 不同文本 → cos≈0.94，
 * 高于默认阈值 0.65 命中；低于阈值 1.0 触发兜底）；V_other 为一半 +0.5 一半 -0.5（与 V_config
 * 夹角≈90°，cos≈0 < 0.65 必然兜底）。</p>
 *
 * <p>覆盖：兜底零模型调用、命中带引用、引用编号与 sources 对齐、引用越界保护、控制器契约。
 * 阈值参数化用例见 {@code QaServiceFallbackTest}（阈值 1.0 需独立上下文）。</p>
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class QaServiceTest {

    private static final int DIMENSIONS = 1024;

    @Autowired
    QaService qaService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    VectorStore vectorStore;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EmbeddingModel embeddingModel;

    /** 挡住 demo seeder：避免它调用未 stub 的 embedding。 */
    @MockitoBean
    FakeDocumentSeeder fakeDocumentSeeder;

    /** 替换真实 ChatClient bean：ask 链路可预测，兜底路径验证零模型调用。 */
    @MockitoBean
    ChatClient chatClient;

    private ChatClientRequestSpec requestSpec;

    private CallResponseSpec callSpec;

    @BeforeAll
    static void ensureTestDatabase() throws Exception {
        String exists = dockerPsql("SELECT 1 FROM pg_database WHERE datname='springkb_test'");
        if (!exists.contains("1")) {
            dockerPsql("CREATE DATABASE springkb_test");
        }
        // 清空历史数据 + drop HNSW 索引强制 seq scan（保证相似度搜索精确返回全部匹配行）
        dockerPsqlDb("DELETE FROM vector_store");
        dockerPsqlDb("DELETE FROM kb_chunk");
        dockerPsqlDb("DELETE FROM kb_document");
        dockerPsqlDb("DROP INDEX IF EXISTS spring_ai_vector_index");
    }

    @BeforeEach
    void setUp() {
        // 每个用例独立：清库（索引已 drop，DELETE 不涉及索引结构）
        jdbcTemplate.update("DELETE FROM vector_store");
        jdbcTemplate.update("DELETE FROM kb_chunk");
        jdbcTemplate.update("DELETE FROM kb_document");

        stubEmbeddingModel();
        stubChatClient("默认模型答案");
    }

    /** EmbeddingModel 三个入口一致映射：含 config/cds → V_config（扰动），其余 → V_other。 */
    private void stubEmbeddingModel() {
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenAnswer(inv -> {
            EmbeddingRequest req = inv.getArgument(0);
            List<Embedding> embeddings = req.getInstructions().stream()
                    .map(text -> new Embedding(vectorFor(text), 0))
                    .toList();
            return new EmbeddingResponse(embeddings);
        });
        // PgVectorStore.doAdd 直接调用该 default 方法；mock 下 default 方法不会执行真实实现，需显式 stub
        when(embeddingModel.embed(anyList(), any(EmbeddingOptions.class), any(BatchingStrategy.class)))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    List<Document> docs = (List<Document>) inv.getArgument(0);
                    return docs.stream().map(d -> vectorFor(d.getText())).toList();
                });
        // doSimilaritySearch 生成 query 向量走单参数 embed(String)；mock 下同样需显式 stub
        when(embeddingModel.embed(anyString())).thenAnswer(inv -> vectorFor(inv.getArgument(0)));
    }

    /** ChatClient 链式 mock：prompt(Prompt) → call() → content()。 */
    private void stubChatClient(String answer) {
        requestSpec = mock(ChatClientRequestSpec.class);
        callSpec = mock(CallResponseSpec.class);
        when(chatClient.prompt(org.mockito.ArgumentMatchers.<Prompt>any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(answer);
    }

    private void stubAnswer(String answer) {
        when(callSpec.content()).thenReturn(answer);
    }

    // ---------- 1. 兜底零模型调用：低相似度问题不调用 ChatClient ----------
    @Test
    void lowSimilarityQuestionShortCircuitsWithoutModelCall() {
        seedChunk("qa-w-" + uid(), "How to config CDS in Spring Boot",
                "official", "3.x", "Spring Boot CDS", "deployment/cds");

        QaResult result = qaService.ask("今天天气怎么样", null, 5);

        assertThat(result.found()).isFalse();
        assertThat(result.answer()).isEqualTo(QaService.NO_ANSWER);
        assertThat(result.sources()).isEmpty();
        verify(chatClient, never()).prompt(org.mockito.ArgumentMatchers.<Prompt>any());
    }

    // ---------- 2. 命中问题带引用：answer 非空、sources 与库内 kb_document 一致 ----------
    @Test
    void matchedQuestionReturnsAnswerWithSources() {
        stubAnswer("[1] 官方文档说明配置 CDS 的步骤；[2] 社区补充实战经验。");
        seedChunk("qa-o-" + uid(), "How to config CDS in Spring Boot official",
                "official", "3.x", "Spring Boot CDS", "deployment/cds");
        seedChunk("qa-c-" + uid(), "How to config CDS in Spring Boot community",
                "community", "3.3", "SO: CDS Config", "deployment/cds");

        QaResult result = qaService.ask("如何配置 CDS", null, 5);

        assertThat(result.found()).isTrue();
        assertThat(result.answer()).isNotBlank();
        assertThat(result.sources()).hasSize(2);
        assertThat(result.sources().get(0).index()).isEqualTo(1);
        assertThat(result.sources().get(0).source()).isEqualTo("official");
        assertThat(result.sources().get(0).version()).isEqualTo("3.x");
        assertThat(result.sources().get(0).title()).isEqualTo("Spring Boot CDS");
        assertThat(result.sources().get(0).headingPath()).isEqualTo("deployment/cds");
        assertThat(result.sources().get(0).snippet()).isNotBlank();
        assertThat(result.sources().get(1).index()).isEqualTo(2);
        assertThat(result.sources().get(1).source()).isEqualTo("community");
        assertThat(result.sources().get(1).version()).isEqualTo("3.3");
        assertThat(result.sources().get(1).title()).isEqualTo("SO: CDS Config");
        assertThat(result.sources().get(1).snippet()).isNotBlank();
    }

    // ---------- 3. 引用编号与 sources 对齐：answer 的引用编号 ⊆ {1..sources.size()} ----------
    @Test
    void citationNumbersAlignWithSourceIndexes() {
        stubAnswer("[1] 来自官方文档 [2] 来自社区");
        seedChunk("qa-a-" + uid(), "How to config CDS in Spring Boot official",
                "official", "3.x", "Spring Boot CDS", "deployment/cds");
        seedChunk("qa-b-" + uid(), "How to config CDS in Spring Boot community",
                "community", "3.3", "SO: CDS Config", "deployment/cds");

        QaResult result = qaService.ask("如何配置 CDS", null, 5);

        assertThat(result.found()).isTrue();
        assertThat(result.sources()).hasSize(2);
        // 编号必须与 sources 的 index 一一对应（1、2 都存在且有序）
        assertThat(result.sources()).extracting(SourceRef::index).containsExactly(1, 2);
    }

    // ---------- 4. 引用越界保护：模型编造 [9] 不抛异常、sources 仍按回源权威列表兜住 ----------
    @Test
    void outOfRangeCitationIsTolerated() {
        stubAnswer("根据官方文档[9]配置 CDS");
        seedChunk("qa-x-" + uid(), "How to config CDS in Spring Boot",
                "official", "3.x", "Spring Boot CDS", "deployment/cds");

        QaResult result = qaService.ask("如何配置 CDS", null, 5);

        assertThat(result.found()).isTrue();
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().get(0).index()).isEqualTo(1);
        assertThat(result.answer()).contains("[9]");
    }

    // ---------- 5. 阈值参数化见 QaServiceFallbackTest（阈值 1.0 需独立上下文） ----------

    // ---------- 6. 控制器契约：question 必填 400；合法请求 code=0 且 data.found 正确 ----------
    @Test
    void controllerValidatesQuestionAndReturnsFound() throws Exception {
        mockMvc.perform(post("/api/qa/ask").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        seedChunk("qa-mvc-" + uid(), "How to config CDS in Spring Boot",
                "official", "3.x", "Spring Boot CDS", "deployment/cds");
        stubAnswer("[1] 官方文档说明配置 CDS 的步骤。");

        mockMvc.perform(post("/api/qa/ask").contentType(APPLICATION_JSON)
                        .content("{\"question\":\"如何配置 CDS\",\"version\":\"3.x\",\"topK\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.found").value(true))
                .andExpect(jsonPath("$.data.answer").isNotEmpty())
                .andExpect(jsonPath("$.data.sources[0].index").value(1));
    }

    // ---------- 7. 模型调用失败：转领域异常，控制器返回 503 而不是挂起 ----------
    @Test
    void modelFailureMapsTo503() throws Exception {
        seedChunk("qa-f-" + uid(), "How to config CDS in Spring Boot",
                "official", "3.x", "Spring Boot CDS", "deployment/cds");
        when(callSpec.content()).thenThrow(new IllegalStateException("connection reset"));

        assertThatThrownBy(() -> qaService.ask("如何配置 CDS", null, 5))
                .isInstanceOf(ModelUnavailableException.class)
                .hasCauseInstanceOf(IllegalStateException.class);

        mockMvc.perform(post("/api/qa/ask").contentType(APPLICATION_JSON)
                        .content("{\"question\":\"如何配置 CDS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("模型服务暂时不可用，请稍后重试"));
    }

    // ---------- helpers ----------

    /** 向量映射：含 config/cds（不区分大小写）→ V_config（全 +0.5 按 hash 扰动 16 维），其余 → V_other（正交）。 */
    private static float[] vectorFor(String text) {
        if (text != null && (text.toLowerCase().contains("config") || text.toLowerCase().contains("cds"))) {
            float[] v = new float[DIMENSIONS];
            Arrays.fill(v, 0.5f);
            int h = text.hashCode();
            for (int i = 0; i < 16; i++) {
                v[Math.floorMod(h + i * 61, DIMENSIONS)] = -0.5f;
            }
            return v;
        }
        float[] v = new float[DIMENSIONS];
        Arrays.fill(v, 0, DIMENSIONS / 2, 0.5f);
        Arrays.fill(v, DIMENSIONS / 2, DIMENSIONS, -0.5f);
        return v;
    }

    /** 同时写 vector_store（含 §4.4 metadata）与 kb_document/kb_chunk（回源依赖），保证三处一致。 */
    private String seedChunk(String docId, String text, String authority, String version, String title, String section) {
        String chunkId = docId + "-0";
        vectorStore.add(List.of(Document.builder()
                .id(chunkId)
                .text(text)
                .metadata(Map.of(
                        "doc_id", docId,
                        "product", "spring-boot",
                        "origin", "spring.io",
                        "authority", authority,
                        "version", version,
                        "active", "true",
                        "doc_type", "reference",
                        "language", "en",
                        "title", title))
                .build()));
        jdbcTemplate.update("""
                INSERT INTO kb_document
                    (id, product, title, origin, authority, version, doc_key, doc_type, language, section, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true)
                """, docId, "spring-boot", title, "spring.io", authority, version,
                "/qa/" + docId + ".html", "reference", "en", section);
        jdbcTemplate.update("""
                INSERT INTO kb_chunk (id, doc_id, ordinal, heading_path, content_markdown, content_text, token_count, image_refs)
                VALUES (?, ?, 0, ?, ?, ?, 0, '[]'::jsonb)
                """, chunkId, docId, section == null ? "" : section, text, text);
        return chunkId;
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
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

    /** 在 springkb_test 专用库执行 SQL（表级操作；dockerPsql 默认连 postgres 库）。 */
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
