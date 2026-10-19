package com.guangqian.springkb.rag;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Reranker 纯 JVM 单测（不碰数据库）：权威性加权、稳定排序、溢出防护、空候选、截取。
 */
class RerankerTest {

    private final Reranker reranker = new Reranker();

    private static Document doc(String id, String authority, double score) {
        return Document.builder()
                .id(id)
                .text("content " + id)
                .metadata(Map.of("authority", authority))
                .score(score)
                .build();
    }

    // 1. official 排前且 community 仍在：community score=0.90、official score=0.80 → 0.80+0.15=0.95 > 0.90
    @Test
    void officialRankedFirstAndCommunityKept() {
        Document community = doc("community", "community", 0.90);
        Document official = doc("official", "official", 0.80);

        List<Document> reranked = reranker.rerank(List.of(community, official));

        assertThat(reranked).extracting(Document::getId).containsExactly("official", "community");
    }

    // 2. 同分稳定性：两个 official 同分保持原顺序；official 与 community 原始同分 → official 因 boost 在前
    @Test
    void stableOrderForEqualScores() {
        Document o1 = doc("o1", "official", 0.80);
        Document o2 = doc("o2", "official", 0.80);
        assertThat(reranker.rerank(List.of(o1, o2)))
                .extracting(Document::getId).containsExactly("o1", "o2");

        Document officialA = doc("oa", "official", 0.80);
        Document officialB = doc("ob", "official", 0.80);
        Document community = doc("c", "community", 0.80);
        assertThat(reranker.rerank(List.of(officialA, community, officialB)))
                .extracting(Document::getId).containsExactly("oa", "ob", "c");
    }

    // 3. boost 溢出防护：score=1.0 的 official → score'=1.15，不抛异常、排序正确、原始 score 不变
    @Test
    void boostOverflowIsSafe() {
        Document official = doc("official", "official", 1.0);
        Document community = doc("community", "community", 1.0);

        List<Document> reranked = reranker.rerank(List.of(official, community));

        assertThat(reranked).extracting(Document::getId).containsExactly("official", "community");
        assertThat(reranked).extracting(Document::getScore).containsExactly(1.0, 1.0);
    }

    // 4. 空候选安全：空列表 → 返回空列表，不抛异常
    @Test
    void emptyCandidatesReturnEmpty() {
        assertThat(reranker.rerank(List.of())).isEmpty();
    }

    // 5. 截取：rerankAndTop 超过候选数时返回全部，不越界；小于候选数时先重排再截取
    @Test
    void rerankAndTopTruncatesAndDoesNotOverrun() {
        RagSearchService service = new RagSearchService(mock(VectorStore.class), reranker);
        List<Document> candidates = List.of(
                doc("community", "community", 0.90),
                doc("official", "official", 0.80));

        assertThat(service.rerankAndTop(candidates, 5))
                .extracting(Document::getId).containsExactly("official", "community");
        assertThat(service.rerankAndTop(candidates, 1))
                .extracting(Document::getId).containsExactly("official");
    }
}
