package com.guangqian.springkb.rag;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
import static org.mockito.Mockito.when;

/**
 * 检索链路诊断（§6.6）集成测试：用确定性 mock Embedding 构造「命中」与「兜底」两条路径，
 * 断言五个阶段齐全且顺序正确、阈值判定与 ask 语义一致、候选回源完整。
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
class RagTraceServiceTest {

    private static final int DIMENSIONS = 1024;

    private static final List<String> STAGE_KEYS = List.of("embedding", "search", "threshold", "rerank", "resolve");

    @Autowired
    RagTraceService ragTraceService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    VectorStore vectorStore;

    @MockitoBean
    EmbeddingModel embeddingModel;

    /** 挡住 demo seeder：避免它调用未 stub 的 embedding。 */
    @MockitoBean
    FakeDocumentSeeder fakeDocumentSeeder;

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

    // ---------- 1. 命中路径：阶段顺序 / 状态 / 回源完整 ----------
    @Test
    @SuppressWarnings("unchecked")
    void traceRecordsAllStagesForMatchedQuery() {
        seedChunk("trace-hit", "How to config CDS in Spring Boot", "official", "3.x",
                "Spring Boot CDS", "deployment/cds");

        Map<String, Object> trace = ragTraceService.trace("如何配置 CDS", null, 5);

        List<Map<String, Object>> stages = (List<Map<String, Object>>) trace.get("stages");
        assertThat(stages).extracting(s -> s.get("key")).containsExactlyElementsOf(STAGE_KEYS);
        assertThat(stages).allSatisfy(s -> assertThat(s.get("elapsedMs")).isInstanceOf(Number.class));

        Map<String, Object> decision = (Map<String, Object>) trace.get("decision");
        assertThat(decision.get("found")).isEqualTo(true);
        assertThat(decision.get("chatInvoked")).isEqualTo(false);

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) trace.get("candidates");
        assertThat(candidates).isNotEmpty();
        assertThat(candidates).allSatisfy(c -> assertThat(c.get("resolved")).isEqualTo(true));

        Map<String, Object> stage = stages.get(0);
        Map<String, Object> embeddingDetail = (Map<String, Object>) stage.get("detail");
        assertThat(embeddingDetail.get("dimensions")).isEqualTo(DIMENSIONS);
    }

    // ---------- 2. 兜底路径：无关问题 → threshold BLOCKED，且不调用模型 ----------
    @Test
    @SuppressWarnings("unchecked")
    void traceMarksThresholdBlockedForUnrelatedQuery() {
        seedChunk("trace-miss", "How to config CDS in Spring Boot", "official", "3.x",
                "Spring Boot CDS", "deployment/cds");

        Map<String, Object> trace = ragTraceService.trace("Vue Router 父子路由怎么配置", null, 5);

        List<Map<String, Object>> stages = (List<Map<String, Object>>) trace.get("stages");
        Map<String, Object> threshold = stages.stream()
                .filter(s -> "threshold".equals(s.get("key")))
                .findFirst()
                .orElseThrow();
        assertThat(threshold.get("status")).isEqualTo("BLOCKED");

        Map<String, Object> decision = (Map<String, Object>) trace.get("decision");
        assertThat(decision.get("found")).isEqualTo(false);
        assertThat(decision.get("chatInvoked")).isEqualTo(false);
    }

    // ---------- 3. 空库：阶段仍然完整，不抛异常 ----------
    @Test
    @SuppressWarnings("unchecked")
    void traceHandlesEmptyKnowledgeBase() {
        Map<String, Object> trace = ragTraceService.trace("任意问题", null, 5);

        List<Map<String, Object>> stages = (List<Map<String, Object>>) trace.get("stages");
        assertThat(stages).extracting(s -> s.get("key")).containsExactlyElementsOf(STAGE_KEYS);
        Map<String, Object> decision = (Map<String, Object>) trace.get("decision");
        assertThat(decision.get("found")).isEqualTo(false);
        assertThat(decision.get("topScore")).isEqualTo(0.0d);
    }

    // ---------- helpers ----------

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

    private void seedChunk(String docId, String text, String authority, String version,
            String title, String section) {
        String chunkId = docId + "-0";
        vectorStore.add(List.of(Document.builder()
                .id(chunkId)
                .text(text)
                .metadata(Map.ofEntries(
                        Map.entry("doc_id", docId),
                        Map.entry("product", "spring-boot"),
                        Map.entry("origin", "spring.io"),
                        Map.entry("authority", authority),
                        Map.entry("version", version),
                        Map.entry("active", "true"),
                        Map.entry("visibility", "team"),
                        Map.entry("review_status", "approved"),
                        Map.entry("doc_type", "reference"),
                        Map.entry("language", "en"),
                        Map.entry("title", title)))
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
                """, chunkId, docId, section, text, text);
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
