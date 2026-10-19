package com.guangqian.springkb.ingestion;

import java.util.List;

import com.guangqian.springkb.crawler.HeadingNode;
import com.guangqian.springkb.crawler.ImageRef;
import com.guangqian.springkb.crawler.RawDocument;
import com.guangqian.springkb.demo.FakeDocumentSeeder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

/**
 * 超大文档分批向量化回归（Task 5 真实数据暴露）：
 * 百炼 text-embedding-v4 单请求输入上限 20 条，>20 chunk 的文档必须分批调用 vectorStore.add。
 * 用 mock VectorStore 断言：至少 2 批、每批 ≤ 20、批内文档总数 == splitter 产出的 chunk 数。
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
class IngestionBatchTest {

    @Autowired
    IngestionService ingestionService;

    @Autowired
    ChunkSplitter chunkSplitter;

    /** 替换真实 PgVectorStore：只记录 add 调用，不写库不调真实 Embedding。 */
    @MockitoBean
    VectorStore vectorStore;

    @MockitoBean
    EmbeddingModel embeddingModel;

    @MockitoBean
    FakeDocumentSeeder fakeDocumentSeeder;

    @Test
    void splitsLargeChunkListsIntoBatchesOfAtMost20() {
        // 25 个章节 × ~1000 token：每章节独立成块 → 25 chunks > 8
        // 每次运行用唯一 docKey：避免幂等命中导致 vectorStore.add 不被调用
        String uid = java.util.UUID.randomUUID().toString().substring(0, 8);
        StringBuilder md = new StringBuilder("# Big Doc " + uid + "\n");
        for (int i = 1; i <= 25; i++) {
            md.append("## Section ").append(i).append("\n").append(text(1000)).append("\n");
        }
        RawDocument doc = new RawDocument(
                "springboot", "spring-boot", "4.2-SNAPSHOT", "en", "reference",
                "Big Doc " + uid, "https://docs.spring.io/spring-boot/big-" + uid + ".html", "big-" + uid,
                "reference", List.of(), "", "", md.toString(),
                List.of(new ImageRef("https://img.example.com/big.png", "Big")),
                List.of(new HeadingNode(1, "Big Doc " + uid, "big")));

        int expectedChunks = chunkSplitter.split(doc).size();
        assertThat(expectedChunks).as("测试前提：必须产出 >8 chunk").isGreaterThan(8);

        ingestionService.importDocument(doc);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, atLeast(2)).add(captor.capture());
        List<List<Document>> batches = captor.getAllValues();

        assertThat(batches).as("应按批提交").hasSizeGreaterThan(1);
        int total = 0;
        for (List<Document> batch : batches) {
            assertThat(batch.size()).as("单批 ≤ 8（百炼批量上限 10 留余量）").isLessThanOrEqualTo(8);
            total += batch.size();
        }
        assertThat(total).isEqualTo(expectedChunks);
    }

    private static String text(int tokens) {
        String sentence = "This is a sample sentence with enough words to fill the budget. ";
        StringBuilder sb = new StringBuilder();
        while (sb.length() < tokens * 4) {
            sb.append(sentence);
        }
        return sb.toString();
    }
}
