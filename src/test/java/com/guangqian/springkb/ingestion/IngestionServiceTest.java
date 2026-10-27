package com.guangqian.springkb.ingestion;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.guangqian.springkb.crawler.HeadingNode;
import com.guangqian.springkb.crawler.ImageRef;
import com.guangqian.springkb.crawler.RawDocument;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 导入链路集成测试：真实 PostgreSQL（springkb_test 专用库），EmbeddingModel 用确定性 mock（1024 维，±0.5 按文本 hash 填充），
 * 不调用真实百炼 API。覆盖：幂等、双字段一致性、vector_store 对齐、空文档不入库。
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
class IngestionServiceTest {

    @Autowired
    IngestionService ingestionService;

    @Autowired
    ChunkSplitter chunkSplitter;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    JsonMapper jsonMapper;

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
    }

    // ---------- 1. 幂等：重复导入不产生重复数据 ----------
    @Test
    void importDocumentIsIdempotent() {
        RawDocument doc = sampleDoc(uid());
        String docId = IngestionService.buildDocId(doc);

        ImportResult first = ingestionService.importDocument(doc);
        assertThat(first.status()).isEqualTo(ImportResult.Status.IMPORTED);
        assertThat(first.chunkCount()).isGreaterThan(0);

        int chunkRows = count("SELECT count(*) FROM kb_chunk WHERE doc_id = ?", docId);
        int vecRows = count("SELECT count(*) FROM vector_store WHERE metadata ->> 'doc_id' = ?", docId);
        assertThat(chunkRows).isGreaterThan(0);
        assertThat(vecRows).isEqualTo(chunkRows);

        ImportResult second = ingestionService.importDocument(doc);
        assertThat(second.status()).isEqualTo(ImportResult.Status.SKIPPED);
        assertThat(second.chunkCount()).isZero();

        assertThat(count("SELECT count(*) FROM kb_document WHERE id = ?", docId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM kb_chunk WHERE doc_id = ?", docId)).isEqualTo(chunkRows);
        assertThat(count("SELECT count(*) FROM vector_store WHERE metadata ->> 'doc_id' = ?", docId)).isEqualTo(vecRows);
    }

    // ---------- 2. 双字段一致性：kb_chunk 与 TextChunk 期望一致 ----------
    @Test
    void chunkFieldsMatchSplitterOutput() throws Exception {
        RawDocument doc = sampleDoc(uid());
        List<TextChunk> expected = chunkSplitter.split(doc);
        assertThat(expected).isNotEmpty();

        ingestionService.importDocument(doc);
        String docId = IngestionService.buildDocId(doc);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT ordinal, heading_path, content_markdown, content_text, token_count, image_refs::text AS image_refs
                FROM kb_chunk WHERE doc_id = ? ORDER BY ordinal
                """, docId);

        assertThat(rows).hasSize(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            TextChunk exp = expected.get(i);
            Map<String, Object> row = rows.get(i);
            assertThat(((Number) row.get("ordinal")).intValue()).isEqualTo(exp.ordinal());
            assertThat(row.get("heading_path")).isEqualTo(exp.headingPath());
            assertThat(row.get("content_markdown")).isEqualTo(exp.contentMarkdown());
            assertThat(row.get("content_text")).isEqualTo(exp.contentText());
            assertThat(((Number) row.get("token_count")).intValue()).isEqualTo(exp.tokenCount());
            List<ImageRef> parsed = jsonMapper.readValue((String) row.get("image_refs"), new TypeReference<>() {
            });
            assertThat(parsed).isEqualTo(exp.imageRefs());
        }
    }

    // ---------- 3. vector_store 与 kb_chunk 按 id 1:1 对齐 ----------
    @Test
    void vectorStoreIdsAlignWithChunkIds() {
        RawDocument doc = sampleDoc(uid());
        ingestionService.importDocument(doc);
        String docId = IngestionService.buildDocId(doc);

        List<String> chunkIds = jdbcTemplate.queryForList(
                "SELECT id FROM kb_chunk WHERE doc_id = ? ORDER BY ordinal", String.class, docId);
        List<String> vecIds = jdbcTemplate.queryForList(
                "SELECT id FROM vector_store WHERE metadata ->> 'doc_id' = ? ORDER BY id", String.class, docId);

        assertThat(chunkIds).isNotEmpty();
        assertThat(vecIds).hasSameElementsAs(chunkIds);
    }

    // ---------- 4. 空文档：SKIPPED 且不入库 ----------
    @Test
    void emptyDocumentIsSkippedAndNotStored() {
        RawDocument emptyDoc = emptyDoc(uid());
        String docId = IngestionService.buildDocId(emptyDoc);

        ImportResult r = ingestionService.importDocument(emptyDoc);

        assertThat(r.status()).isEqualTo(ImportResult.Status.SKIPPED);
        assertThat(r.chunkCount()).isZero();
        assertThat(count("SELECT count(*) FROM kb_document WHERE id = ?", docId)).isZero();
        assertThat(count("SELECT count(*) FROM kb_chunk WHERE doc_id = ?", docId)).isZero();
        assertThat(count("SELECT count(*) FROM vector_store WHERE metadata ->> 'doc_id' = ?", docId)).isZero();
    }

    // ---------- 5. 批量导入进度回调：每篇一次、计数单调递增、汇总与返回值一致 ----------
    @Test
    void importAllReportsProgressPerDocument() {
        List<RawDocument> docs = List.of(sampleDoc(uid()), sampleDoc(uid()), emptyDoc(uid()));
        List<Integer> processedSeq = new ArrayList<>();
        List<ImportSummary> summaries = new ArrayList<>();

        ImportSummary result = ingestionService.importAll(docs, (processed, soFar) -> {
            processedSeq.add(processed);
            summaries.add(soFar);
        });

        // 回调次数 == 文档数，序号 1..n 连续
        assertThat(processedSeq).containsExactly(1, 2, 3);
        // 累计计数单调不减，且与最终返回值一致
        assertThat(summaries.get(2).imported()).isEqualTo(result.imported());
        assertThat(summaries.get(2).skipped()).isEqualTo(result.skipped());
        assertThat(result.imported() + result.skipped() + result.failed()).isEqualTo(docs.size());
    }

    // ---------- 6. docKey 规范化碰撞：第二篇自动加哈希后缀，两篇独立入库且幂等 ----------
    @Test
    void normalizationCollisionFallsBackToHashedDocId() {
        String uid = uid();
        // "genindex-<uid>" 与 "genindex-<uid>_" 规范化后同为 "genindex-<uid>"（python genindex / genindex-_）
        RawDocument first = sampleDoc(uid, "genindex-" + uid);
        RawDocument second = sampleDoc(uid, "genindex-" + uid + "_");
        assertThat(IngestionService.buildDocId(first)).isEqualTo(IngestionService.buildDocId(second));

        ImportResult r1 = ingestionService.importDocument(first);
        ImportResult r2 = ingestionService.importDocument(second);

        assertThat(r1.status()).isEqualTo(ImportResult.Status.IMPORTED);
        assertThat(r2.status()).isEqualTo(ImportResult.Status.IMPORTED);
        assertThat(r2.docId()).isNotEqualTo(r1.docId())
                .startsWith(IngestionService.buildDocId(first) + "-" + IngestionService.shortHash(second.docKey()));

        assertThat(count("SELECT count(*) FROM kb_document WHERE doc_key IN (?, ?)",
                first.docKey(), second.docKey())).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM vector_store WHERE metadata ->> 'doc_id' IN (?, ?)",
                r1.docId(), r2.docId())).isGreaterThan(0);

        // 再次导入仍幂等（按原始 doc_key 命中，不会因哈希后缀重复入库）
        assertThat(ingestionService.importDocument(second).status()).isEqualTo(ImportResult.Status.SKIPPED);
        assertThat(count("SELECT count(*) FROM kb_document WHERE doc_key = ?", second.docKey())).isEqualTo(1);
    }

    // ---------- 7. 短哈希：确定性、长度 10、不同 docKey 不相等 ----------
    @Test
    void shortHashIsStableAndDistinct() {
        assertThat(IngestionService.shortHash("genindex-abc")).isEqualTo(IngestionService.shortHash("genindex-abc"));
        assertThat(IngestionService.shortHash("genindex-abc")).hasSize(10)
                .isNotEqualTo(IngestionService.shortHash("genindex-abc_"));
    }

    // ---------- helpers ----------

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private RawDocument sampleDoc(String uid) {
        return sampleDoc(uid, "/guide/test-" + uid + ".html");
    }

    private RawDocument sampleDoc(String uid, String docKey) {
        String markdown = """
                # Test Guide %s
                ## Section Alpha %s
                %s
                ## Section Beta %s
                %s
                """.formatted(uid, uid, text(600), uid, text(600));
        return new RawDocument(
                "vue", "vue", "3.x", "en", "guide", "Test Guide " + uid,
                "https://vuejs.org/guide/test-" + uid + ".html", docKey,
                "guide", List.of(), "2026-01-01T00:00:00Z", "", markdown,
                List.of(new ImageRef("https://img.example.com/" + uid + ".png", "Img " + uid)),
                List.of(new HeadingNode(1, "Test Guide " + uid, "test-guide"),
                        new HeadingNode(2, "Section Alpha " + uid, "section-alpha"),
                        new HeadingNode(2, "Section Beta " + uid, "section-beta")));
    }

    private RawDocument emptyDoc(String uid) {
        return new RawDocument(
                "vue", "vue", "3.x", "en", "guide", "Empty Guide " + uid,
                "https://vuejs.org/guide/empty-" + uid + ".html", "/guide/empty-" + uid + ".html",
                "guide", List.of(), "", "", "",
                List.of(), List.of(new HeadingNode(1, "Empty Guide " + uid, "empty-guide")));
    }

    private static String text(int tokens) {
        String sentence = "This is a sample sentence with enough words to fill the budget. ";
        StringBuilder sb = new StringBuilder();
        while (sb.length() < tokens * 4) {
            sb.append(sentence);
        }
        return sb.toString();
    }

    /** 确定性 1024 维向量：按文本 hashCode 填充 ±0.5，避免全零向量。 */
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
        int code = p.waitFor();
        if (code != 0) {
            throw new IllegalStateException("docker exec psql 失败: " + out);
        }
        return out.trim();
    }
}
