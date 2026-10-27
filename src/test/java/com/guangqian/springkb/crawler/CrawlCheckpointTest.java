package com.guangqian.springkb.crawler;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 断点记录纯单元测试（不触网）：验证状态标记、URL 去重、断点文档读写与合并。
 */
class CrawlCheckpointTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void restoreBaseDir() {
        CrawlCheckpoint.useBaseDir(Path.of("data", "raw"));
    }

    @Test
    void marksStateAndClearsPartial() {
        CrawlCheckpoint.useBaseDir(tempDir);

        assertThat(CrawlCheckpoint.isDone("demo")).isFalse();

        CrawlCheckpoint.markPartial("demo", 3);
        assertThat(CrawlCheckpoint.isDone("demo")).isFalse();

        CrawlCheckpoint.markDone("demo", 10);
        assertThat(CrawlCheckpoint.isDone("demo")).isTrue();
    }

    @Test
    void appendsDocumentsAndSkipsCrawledUrls() {
        CrawlCheckpoint.useBaseDir(tempDir);

        CrawlCheckpoint.append("demo", doc("https://demo.test/a", "a"));
        CrawlCheckpoint.append("demo", doc("https://demo.test/b", "b"));

        assertThat(CrawlCheckpoint.doneUrls("demo"))
                .containsExactly("https://demo.test/a", "https://demo.test/b");
        List<RawDocument> partial = CrawlCheckpoint.loadPartial("demo");
        assertThat(partial).hasSize(2);
        assertThat(partial.get(0).docKey()).isEqualTo("a");
        assertThat(partial.get(1).title()).isEqualTo("b");

        CrawlCheckpoint.clearPartial("demo");
        assertThat(CrawlCheckpoint.loadPartial("demo")).isEmpty();
        assertThat(CrawlCheckpoint.doneUrls("demo")).isEmpty();
    }

    @Test
    void mergeDeduplicatesByUrlAndKeepsOrder() {
        RawDocument a = doc("https://demo.test/a", "a");
        RawDocument b = doc("https://demo.test/b", "b");
        RawDocument bAgain = doc("https://demo.test/b", "b-new");

        List<RawDocument> merged = CrawlCheckpoint.merge(List.of(a, b), List.of(bAgain, doc("https://demo.test/c", "c")));

        assertThat(merged).extracting(RawDocument::docKey).containsExactly("a", "b-new", "c");
    }

    private static RawDocument doc(String url, String key) {
        return new RawDocument("demo", "demo", "latest", "zh", "guide", key, url, key, null,
                List.of(), "", "text", "# markdown", List.of(), List.of());
    }
}
