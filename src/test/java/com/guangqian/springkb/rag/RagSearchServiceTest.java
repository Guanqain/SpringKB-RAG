package com.guangqian.springkb.rag;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.guangqian.springkb.crawler.HeadingNode;
import com.guangqian.springkb.crawler.RawDocument;
import com.guangqian.springkb.demo.FakeDocumentSeeder;
import com.guangqian.springkb.ingestion.IngestionService;
import com.guangqian.springkb.ingestion.ImportResult;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 版本过滤检索集成测试：真实 PostgreSQL（springkb_test 专用库，允许残留），
 * EmbeddingModel 用确定性 mock（全同号向量，保证任意 query 与文档余弦相似度为正，
 * 不会被 similarityThreshold=0.0 过滤），FakeDocumentSeeder mock 防干扰。
 * 覆盖：版本过滤、过滤下推证明、inactive 不召回、空版本默认、注入拒绝、§4.4 元数据落地。
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RagSearchServiceTest {

    private static final int DIMENSIONS = 1024;
    private static final String QUERY = "Spring AI RAG retrieval with version filter";

    @Autowired
    RagSearchService ragSearchService;

    @Autowired
    IngestionService ingestionService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MockMvc mockMvc;

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
        // 1) 清空历史数据：历史残留（Task 1 测试写入的 ±0.5 模式 mock 向量）与本类全 0.5 模式向量的
        //    query 余弦距离 ≈ 1，会被 similarityThreshold=0.0（distance < 1.0）过滤，导致
        //    「filter count」与「搜索返回条数」不一致；清空后库中只有本类数据，两者可精确对齐。
        // 2) drop HNSW 索引强制 seq scan：全同 mock 向量（距离全 0）下 HNSW 贪心遍历会漏节点，
        //    seq scan 才能保证搜索精确返回全部匹配行。
        //    （本类测试上下文在启动时按 initialize-schema 建一次索引，@BeforeAll 在此之后执行，
        //    测试期间不会重建；其他测试类的独立上下文在其自身执行期间才会重建索引。）
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

    // ---------- 1. version=X 只召回 X ----------
    @Test
    void versionFilterOnlyRecallsTargetVersion() {
        RawDocument doc3x = sampleDoc(uid(), "3.x", "RAG Version Filter Doc A");
        RawDocument doc4x = sampleDoc(uid(), "4.x", "RAG Version Filter Doc B");
        ImportResult r3x = ingestionService.importDocument(doc3x);
        ImportResult r4x = ingestionService.importDocument(doc4x);
        assertThat(r3x.status()).isEqualTo(ImportResult.Status.IMPORTED);
        assertThat(r4x.status()).isEqualTo(ImportResult.Status.IMPORTED);
        String docId3x = r3x.docId();
        String docId4x = r4x.docId();

        List<Map<String, Object>> rows = ragSearchService.search(QUERY, "3.x", 100);

        assertThat(rows).isNotEmpty();
        assertThat(rows).extracting(r -> r.get("doc_id")).doesNotContain(docId4x);
        assertThat(rows).allSatisfy(row -> assertThat(row.get("version")).isEqualTo("3.x"));
    }

    // ---------- 2. 过滤下推证明：topK 远大于总 chunk 数时也不泄漏其他版本 ----------
    @Test
    void filterIsPushedDownToQueryLayer() {
        RawDocument doc3x = sampleDoc(uid(), "3.x", "RAG Version Filter Doc C");
        RawDocument doc4x = sampleDoc(uid(), "4.x", "RAG Version Filter Doc D");
        ImportResult r3x = ingestionService.importDocument(doc3x);
        ImportResult r4x = ingestionService.importDocument(doc4x);
        assertThat(r3x.status()).isEqualTo(ImportResult.Status.IMPORTED);
        assertThat(r4x.status()).isEqualTo(ImportResult.Status.IMPORTED);
        String docId3x = r3x.docId();
        String docId4x = r4x.docId();

        int chunk3x = count("SELECT count(*) FROM kb_chunk WHERE doc_id = ?", docId3x);
        assertThat(chunk3x).isGreaterThanOrEqualTo(2);
        List<String> chunkIds3x = jdbcTemplate.queryForList(
                "SELECT id FROM kb_chunk WHERE doc_id = ?", String.class, docId3x);

        // 库中当前所有 active 且 version=3.x 的 chunk 数（与搜索同时刻，含测试库残留）
        int expected3x = count("SELECT count(*) FROM vector_store WHERE metadata ->> 'version' = '3.x' AND metadata ->> 'active' = 'true'");

        List<Map<String, Object>> rows = ragSearchService.search(QUERY, "3.x", 1000);

        // 返回条数与 SQL 层匹配 count 完全一致：过滤发生在查询层（若内存后过滤，topK 巨大时会混入 4.x）
        assertThat(rows).hasSize(expected3x);
        assertThat(rows).extracting(r -> r.get("doc_id")).doesNotContain(docId4x);
        assertThat(rows).extracting(r -> r.get("id")).containsAll(chunkIds3x);
    }

    // ---------- 3. inactive 不召回 ----------
    @Test
    void inactiveDocumentNotRecalled() {
        RawDocument doc3x = sampleDoc(uid(), "3.x", "RAG Version Filter Doc E");
        RawDocument doc4x = sampleDoc(uid(), "4.x", "RAG Version Filter Doc F");
        ImportResult r3x = ingestionService.importDocument(doc3x);
        ImportResult r4x = ingestionService.importDocument(doc4x);
        assertThat(r3x.status()).isEqualTo(ImportResult.Status.IMPORTED);
        assertThat(r4x.status()).isEqualTo(ImportResult.Status.IMPORTED);
        String docId3x = r3x.docId();
        String docId4x = r4x.docId();

        // 模拟管理端停用：kb_document 与 vector_store 元数据双写 active=false（metadata 列为 json，需 cast）
        jdbcTemplate.update("UPDATE kb_document SET active = false WHERE id = ?", docId3x);
        jdbcTemplate.update("""
                UPDATE vector_store SET metadata = jsonb_set(metadata::jsonb, '{active}', '"false"'::jsonb)::json
                WHERE metadata ->> 'doc_id' = ?
                """, docId3x);

        List<Map<String, Object>> rows = ragSearchService.search(QUERY, null, 1000);

        assertThat(rows).extracting(r -> r.get("doc_id")).doesNotContain(docId3x);
        assertThat(rows).extracting(r -> r.get("doc_id")).contains(docId4x);
    }

    // ---------- 4. 空版本默认值：召回全部活跃版本 ----------
    @Test
    void nullVersionReturnsAllActiveVersions() {
        RawDocument doc3x = sampleDoc(uid(), "3.x", "RAG Version Filter Doc G");
        RawDocument doc4x = sampleDoc(uid(), "4.x", "RAG Version Filter Doc H");
        ImportResult r3x = ingestionService.importDocument(doc3x);
        ImportResult r4x = ingestionService.importDocument(doc4x);
        assertThat(r3x.status()).isEqualTo(ImportResult.Status.IMPORTED);
        assertThat(r4x.status()).isEqualTo(ImportResult.Status.IMPORTED);
        String docId3x = r3x.docId();
        String docId4x = r4x.docId();

        List<Map<String, Object>> rows = ragSearchService.search(QUERY, null, 1000);

        assertThat(rows).extracting(r -> r.get("doc_id")).contains(docId3x, docId4x);
    }

    // ---------- 5. 注入拒绝：非法 version 不产生 SQL 错误、不崩溃；控制器映射 400 ----------
    @Test
    void maliciousVersionRejected() throws Exception {
        String malicious = "3.x' OR '1'='1";

        assertThatThrownBy(() -> ragSearchService.search(QUERY, malicious, 10))
                .isInstanceOf(IllegalArgumentException.class);

        mockMvc.perform(get("/api/qa/search").param("q", "test").param("version", malicious))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ---------- 6. §4.4 元数据落地：键齐全且值与导入一致 ----------
    @Test
    void metadataFollowsSection44Schema() {
        RawDocument doc = sampleDoc(uid(), "3.x", "RAG Version Filter Doc I");
        ImportResult r = ingestionService.importDocument(doc);
        assertThat(r.status()).isEqualTo(ImportResult.Status.IMPORTED);
        String docId = r.docId();

        List<Document> docs = ragSearchService.searchDocuments(QUERY, null, 1000);
        Document hit = docs.stream()
                .filter(d -> docId.equals(d.getMetadata().get("doc_id")))
                .findFirst()
                .orElseThrow();

        Map<String, Object> meta = hit.getMetadata();
        assertThat(meta).containsKeys("doc_id", "product", "origin", "authority", "version",
                "active", "doc_type", "language", "title");
        assertThat(meta.get("doc_id")).isEqualTo(docId);
        assertThat(meta.get("product")).isEqualTo(doc.product());
        assertThat(meta.get("origin")).isEqualTo("spring.io");
        assertThat(meta.get("authority")).isEqualTo("official");
        assertThat(meta.get("version")).isEqualTo("3.x");
        assertThat(meta.get("active")).isEqualTo("true");
        assertThat(meta.get("doc_type")).isEqualTo("reference");
        assertThat(meta.get("language")).isEqualTo("en");
        assertThat(meta.get("title")).isEqualTo(doc.title());
    }

    // ---------- 7. 权威性加权（Task 3）：同正文同向量时 official 靠 boost 排前 ----------
    @Test
    void rerankBoostPutsOfficialFirstWhenRawScoresEqual() {
        // 两篇正文完全相同（→ 相同嵌入 → 相同原始相似度），仅 authority 不同；
        // 用唯一 version 隔离本用例数据，避免本类其他用例的 official 残留干扰 top-2。
        String text = "Authority rerank identical chunk text " + uid();
        String version = "9.x";
        String officialDocId = "rerank-official-" + uid();
        String communityDocId = "rerank-community-" + uid();
        vectorStore.add(List.of(
                Document.builder()
                        .id(officialDocId + "-0")
                        .text(text)
                        .metadata(Map.of(
                                "doc_id", officialDocId, "authority", "official",
                                "version", version, "active", "true", "title", "official title"))
                        .build(),
                Document.builder()
                        .id(communityDocId + "-0")
                        .text(text)
                        .metadata(Map.of(
                                "doc_id", communityDocId, "authority", "community",
                                "version", version, "active", "true", "title", "community title"))
                        .build()));

        List<Map<String, Object>> rows = ragSearchService.search(text, version, 2);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("source")).isEqualTo("official");
        assertThat(rows.get(0).get("doc_id")).isEqualTo(officialDocId);
        assertThat(rows.get(1).get("source")).isEqualTo("community");
        assertThat(rows.get(1).get("doc_id")).isEqualTo(communityDocId);
    }

    // ---------- 控制器契约：q 必填 400、topK 钳制 ----------
    @Test
    void controllerRequiresQAndClampsTopK() throws Exception {
        mockMvc.perform(get("/api/qa/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/api/qa/search").param("q", "test").param("topK", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ---------- helpers ----------

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private RawDocument sampleDoc(String uid, String version, String title) {
        String markdown = """
                # %s
                ## Retrieval Section A %s
                %s
                ## Retrieval Section B %s
                %s
                ## Retrieval Section C %s
                %s
                """.formatted(title, uid, text(600), uid, text(600), uid, text(600));
        return new RawDocument(
                "springboot", "spring-boot", version, "en", "reference", title,
                "https://docs.spring.io/spring-boot/ref/" + uid + ".html",
                "/spring-boot/ref/" + uid + ".html",
                "retrieval", List.of(), "2026-01-01T00:00:00Z", "", markdown,
                List.of(),
                List.of(new HeadingNode(1, title, uid + "-h1"),
                        new HeadingNode(2, "Retrieval Section A " + uid, uid + "-a"),
                        new HeadingNode(2, "Retrieval Section B " + uid, uid + "-b"),
                        new HeadingNode(2, "Retrieval Section C " + uid, uid + "-c")));
    }

    private static String text(int tokens) {
        String sentence = "This is a sample sentence with enough words to fill the budget. ";
        StringBuilder sb = new StringBuilder();
        while (sb.length() < tokens * 4) {
            sb.append(sentence);
        }
        return sb.toString();
    }

    /** 确定性向量：所有维度同号（+0.5），任意 query 与文档余弦相似度 ≈ 1，不会被 similarityThreshold=0.0 过滤。 */
    private static float[] vectorFor(String text) {
        float[] v = new float[DIMENSIONS];
        Arrays.fill(v, 0.5f);
        return v;
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
