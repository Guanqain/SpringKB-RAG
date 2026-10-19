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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阈值参数化测试（§6.4）：将 {@code app.rag.similarity-threshold} 设为 1.0（独立上下文，
 * 与 {@link QaServiceTest} 的默认 0.65 隔离）——即使命中问题（相似度≈0.94 &gt; 默认 0.65）
 * 也触发兜底短路，证明阈值参数生效且不调用 Chat 模型。同时覆盖空版本默认（version=null）。
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "app.rag.similarity-threshold=1.0"
})
@ActiveProfiles("test")
class QaServiceFallbackTest {

    private static final int DIMENSIONS = 1024;

    @Autowired
    QaService qaService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    VectorStore vectorStore;

    @MockitoBean
    EmbeddingModel embeddingModel;

    /** 挡住 demo seeder：避免它调用未 stub 的 embedding。 */
    @MockitoBean
    FakeDocumentSeeder fakeDocumentSeeder;

    /** 替换真实 ChatClient bean：兜底路径验证零模型调用。 */
    @MockitoBean
    ChatClient chatClient;

    @BeforeAll
    static void ensureTestDatabase() throws Exception {
        String exists = dockerPsql("SELECT 1 FROM pg_database WHERE datname='springkb_test'");
        if (!exists.contains("1")) {
            dockerPsql("CREATE DATABASE springkb_test");
        }
        dockerPsqlDb("DELETE FROM vector_store");
        dockerPsqlDb("DELETE FROM kb_chunk");
        dockerPsqlDb("DELETE FROM kb_document");
        dockerPsqlDb("DROP INDEX IF EXISTS spring_ai_vector_index");
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM vector_store");
        jdbcTemplate.update("DELETE FROM kb_chunk");
        jdbcTemplate.update("DELETE FROM kb_document");
        stubEmbeddingModel();
    }

    private void stubEmbeddingModel() {
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
    }

    // ---------- 5. 阈值参数化：similarity-threshold=1.0 时命中问题也触发兜底（零模型调用） ----------
    @Test
    void raisedThresholdForcesFallbackEvenForMatchedQuestion() {
        seedChunk("qa-t-" + uid(), "How to config CDS in Spring Boot",
                "official", "3.x", "Spring Boot CDS", "deployment/cds");

        // 命中问题（默认 0.65 下会 found=true），但阈值 1.0 > 相似度≈0.94 → 兜底
        QaResult result = qaService.ask("如何配置 CDS", null, 5);

        assertThat(result.found()).isFalse();
        assertThat(result.answer()).isEqualTo(QaService.NO_ANSWER);
        assertThat(result.sources()).isEmpty();
        verify(chatClient, never()).prompt(org.mockito.ArgumentMatchers.<Prompt>any());
    }

    // ---------- helpers ----------

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
