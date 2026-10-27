package com.guangqian.springkb.ingestion;

import java.util.List;

import com.guangqian.springkb.crawler.HeadingNode;
import com.guangqian.springkb.crawler.ImageRef;
import com.guangqian.springkb.crawler.RawDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分块器单元测试：纯 JVM，不依赖数据库。
 * 覆盖：标题切分/超长二次切分/代码围栏完整性/空内容/无 headings 兜底/双字段一致性/ordinal 连续性。
 */
class ChunkSplitterTest {

    private final ChunkSplitter splitter = new ChunkSplitter();

    // ---------- 1. 按标题切分：3 个 h2 章节各 ~200 token 应合为 1 块 ----------
    @Test
    void mergesSmallSectionsIntoOneChunkWithHeadingPath() {
        RawDocument doc = doc(
                "# Guide\n\n"
                        + "## Section A\n\n" + text(200) + "\n\n"
                        + "## Section B\n\n" + text(200) + "\n\n"
                        + "## Section C\n\n" + text(200) + "\n",
                List.of(),
                List.of(
                        new HeadingNode(1, "Guide", "guide"),
                        new HeadingNode(2, "Section A", "section-a"),
                        new HeadingNode(2, "Section B", "section-b"),
                        new HeadingNode(2, "Section C", "section-c")));

        List<TextChunk> chunks = splitter.split(doc);

        assertThat(chunks).hasSize(1);
        TextChunk chunk = chunks.get(0);
        assertThat(chunk.ordinal()).isEqualTo(1);
        // 含 h1 栈：块起始于 h1 标题下
        assertThat(chunk.headingPath()).isEqualTo("Guide");
        // 总量约 600 token，在 500~1000 预算内
        assertThat(chunk.tokenCount()).isBetween(500, 1000);
    }

    // ---------- 2. 超长章节二次切分为多块，除尾块外每块不超 maxTokens*1.25 ----------
    @Test
    void splitsOverlongSectionIntoMultipleChunks() {
        RawDocument doc = doc(
                "# Guide\n\n"
                        + "## Big Section\n\n" + text(2000) + "\n",
                List.of(),
                List.of(
                        new HeadingNode(1, "Guide", "guide"),
                        new HeadingNode(2, "Big Section", "big-section")));

        List<TextChunk> chunks = splitter.split(doc);

        assertThat(chunks.size()).isGreaterThan(1);
        for (int i = 0; i < chunks.size() - 1; i++) {
            assertThat(chunks.get(i).tokenCount())
                    .as("非尾块 token 应在 [minTokens, maxTokens*1.25] 区间")
                    .isBetween(500, 1250);
        }
        assertThat(chunks.get(chunks.size() - 1).tokenCount()).isLessThanOrEqualTo(1250);
    }

    // ---------- 3. 代码围栏不截断：围栏整体完整出现在某块 ----------
    @Test
    void keepsCodeFenceIntact() {
        String code = "```java\n" + "int x = 1;\n".repeat(400) + "```";
        RawDocument doc = doc(
                "# Guide\n\n## Code Section\n\n" + code + "\n\nSome trailing explanation here.\n",
                List.of(),
                List.of(
                        new HeadingNode(1, "Guide", "guide"),
                        new HeadingNode(2, "Code Section", "code-section")));

        List<TextChunk> chunks = splitter.split(doc);

        assertThat(chunks).isNotEmpty();
        boolean fenceSeen = false;
        for (TextChunk chunk : chunks) {
            String md = chunk.contentMarkdown();
            long fences = md.lines().filter(l -> l.trim().startsWith("```")).count();
            // 不允许出现围栏出现在块内但未闭合（0 或偶数个围栏行）
            assertThat(fences % 2).as("围栏必须成对出现").isZero();
            if (md.contains("```java")) {
                fenceSeen = true;
                // 围栏内容要么完整出现
                assertThat(md).contains(code);
            }
        }
        assertThat(fenceSeen).as("代码围栏应完整出现在某个块中").isTrue();
    }

    // ---------- 4. 空内容/全空白 → 空列表 ----------
    @Test
    void emptyContentYieldsNoChunks() {
        assertThat(splitter.split(doc(null, List.of(), List.of()))).isEmpty();
        assertThat(splitter.split(doc("   \n  \n", List.of(), List.of()))).isEmpty();
    }

    // ---------- 5. 无 headings 兜底：至少 1 块且 headingPath 非空（用 title） ----------
    @Test
    void fallsBackToTitleWhenHeadingsMissing() {
        RawDocument doc = doc(
                "# Any Header\n\nJust some body text without heading metadata.\n",
                List.of(),
                List.of()); // headings 为空

        List<TextChunk> chunks = splitter.split(doc);

        assertThat(chunks).isNotEmpty();
        for (TextChunk chunk : chunks) {
            assertThat(chunk.headingPath()).isNotBlank();
            assertThat(chunk.headingPath()).isEqualTo("Test Title");
        }
    }

    // ---------- 6. 双字段一致性：contentText 去语法、contentMarkdown 保留结构、图片进 imageRefs ----------
    @Test
    void textAndMarkdownStayConsistent() {
        ImageRef logo = new ImageRef("https://img.example.com/logo.png", "Logo");
        RawDocument doc = doc("""
                # API Guide
                Introduction with `inline` code and a [reference link](https://example.com/ref).
                ![Logo](https://img.example.com/logo.png)
                ```java
                public void main() { }
                ```
                """,
                List.of(logo),
                List.of(new HeadingNode(1, "API Guide", "api-guide")));

        List<TextChunk> chunks = splitter.split(doc);

        assertThat(chunks).hasSize(1);
        TextChunk chunk = chunks.get(0);
        String text = chunk.contentText();
        String md = chunk.contentMarkdown();

        // contentText：无标题标记、无链接语法、无围栏
        assertThat(text.lines().noneMatch(l -> l.trim().startsWith("#")))
                .as("contentText 不应含 # 标题标记").isTrue();
        assertThat(text).doesNotContain("[reference link](https://example.com/ref)");
        assertThat(text).doesNotContain("```");
        // 语法内容被清洗/保留：行内代码、链接文本、图片 alt、代码内容
        assertThat(text).contains("inline");
        assertThat(text).contains("reference link");
        assertThat(text).contains("Logo");
        assertThat(text).contains("public void main() { }");

        // contentMarkdown：保留标题与代码块
        assertThat(md).contains("# API Guide");
        assertThat(md).contains("```java");

        // imageRefs：非空且 src 匹配 doc.images
        assertThat(chunk.imageRefs()).isNotEmpty();
        assertThat(chunk.imageRefs().get(0).src()).isEqualTo(logo.src());
        assertThat(chunk.imageRefs().get(0).alt()).isEqualTo("Logo");
    }

    // ---------- 7. ordinal 连续递增 1..n ----------
    @Test
    void ordinalIsSequential() {
        RawDocument doc = doc(
                "# Guide\n\n"
                        + "## Big Section\n\n" + text(2000) + "\n",
                List.of(),
                List.of(
                        new HeadingNode(1, "Guide", "guide"),
                        new HeadingNode(2, "Big Section", "big-section")));

        List<TextChunk> chunks = splitter.split(doc);

        assertThat(chunks.size()).isGreaterThan(1);
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).ordinal()).isEqualTo(i + 1);
        }
    }

    // ---------- 8. 含 $ 标识符 / 反斜杠的内容清洗不抛异常（真实 Vue 文档回归） ----------
    @Test
    void cleansContentWithDollarSignsAndBackslashes() {
        RawDocument doc = doc("""
                # Component Instance

                Access the `$refs` and `$el` via [the component instance](/api/component-instance.html#instance-properties).
                The store state is exposed as `$store.state`, and `$emit` fires events.
                LaTeX inline \\frac{1}{2}, a [Windows path docs](https://example.com/path), and a raw `C:\\temp\\file`.
                """,
                List.of(),
                List.of(new HeadingNode(1, "Component Instance", "component-instance")));

        List<TextChunk> chunks = splitter.split(doc); // 不得抛 IllegalArgumentException

        assertThat(chunks).hasSize(1);
        String text = chunks.get(0).contentText();
        // 链接语法已剥离，$ 标识符原样保留
        assertThat(text).contains("$refs").contains("$store").contains("$emit");
        assertThat(text).contains("the component instance");
        // 反斜杠不做转义解释
        assertThat(text).contains("\\frac{1}{2}");
        assertThat(text).contains("C:\\temp\\file");
        assertThat(text).contains("Windows path docs");
    }

    // ---------- 9. token 估算：CJK 按 1 token/字符，ASCII 按 4 字符/token ----------
    @Test
    void estimatesCjkAndAsciiTokenWeights() {
        assertThat(TokenEstimator.estimate("中".repeat(100))).isEqualTo(100);
        assertThat(TokenEstimator.estimate("abcd")).isEqualTo(1);
        // 50 个汉字 + 50 个 ASCII ≈ 50 + 12.5 → 63
        assertThat(TokenEstimator.estimate("中a".repeat(50))).isEqualTo(63);
        assertThat(TokenEstimator.estimate("")).isZero();
        assertThat(TokenEstimator.estimate(null)).isZero();
    }

    // ---------- 10. 中文超长章节：按 token 预算切分，不再按 4 字符/token 低估 ----------
    @Test
    void splitsLongChineseSectionByTokenBudget() {
        String zh = "中文文档内容用于验证分块预算与中文token估算。".repeat(400); // 1 万字符 → 约 1 万 token
        RawDocument doc = doc(
                "# 指南\n\n## 长章节\n\n" + zh + "\n",
                List.of(),
                List.of(
                        new HeadingNode(1, "指南", "guide"),
                        new HeadingNode(2, "长章节", "long-section")));

        List<TextChunk> chunks = splitter.split(doc);

        assertThat(chunks.size()).isGreaterThan(1);
        for (TextChunk chunk : chunks) {
            assertThat(chunk.tokenCount())
                    .as("中文块 tokenCount 应按字符数估算且不超预算")
                    .isBetween(1, 1250);
        }
    }

    // ---------- 11. 超大代码围栏：超过 2×maxTokens 时成对闭合重开切分 ----------
    @Test
    void splitsOversizedCodeFenceWithBalancedMarkers() {
        String code = "```java\n" + "int value = compute(1, 2);\n".repeat(2000) + "```";
        RawDocument doc = doc(
                "# Guide\n\n## Code Section\n\n" + code + "\n",
                List.of(),
                List.of(
                        new HeadingNode(1, "Guide", "guide"),
                        new HeadingNode(2, "Code Section", "code-section")));

        List<TextChunk> chunks = splitter.split(doc);

        assertThat(chunks.size()).isGreaterThan(1);
        for (TextChunk chunk : chunks) {
            String md = chunk.contentMarkdown();
            long fences = md.lines().filter(l -> l.trim().startsWith("```")).count();
            assertThat(fences % 2).as("切分后的每块围栏必须成对").isZero();
            assertThat(chunk.tokenCount())
                    .as("单块不得超过硬上限 2×maxTokens")
                    .isLessThanOrEqualTo(2000);
            assertThat(md).doesNotContain(code);
        }
    }

    // ---------- 12. 无换行的超长单行（中文）：按 token 预算切分 ----------
    @Test
    void splitsVeryLongUnbrokenChineseLine() {
        String oneLine = "单行超长中文内容".repeat(600); // 4800 字符且无换行
        RawDocument doc = doc(
                "# 指南\n\n## 长行\n\n" + oneLine + "\n",
                List.of(),
                List.of(
                        new HeadingNode(1, "指南", "guide"),
                        new HeadingNode(2, "长行", "long-line")));

        List<TextChunk> chunks = splitter.split(doc);

        assertThat(chunks.size()).isGreaterThan(1);
        for (TextChunk chunk : chunks) {
            assertThat(chunk.tokenCount()).isLessThanOrEqualTo(1250);
        }
    }

    // ---------- helpers ----------

    /** 构造约 tokens 个 token 的纯文本（1 token ≈ 4 字符）。 */
    private static String text(int tokens) {
        String sentence = "This is a sample sentence with enough words to fill the budget. ";
        StringBuilder sb = new StringBuilder();
        while (sb.length() < tokens * 4) {
            sb.append(sentence);
        }
        return sb.toString();
    }

    private static RawDocument doc(String markdown, List<ImageRef> images, List<HeadingNode> headings) {
        return new RawDocument(
                "vue", "vue", "3.x", "en", "guide", "Test Title",
                "https://vuejs.org/guide/test.html", "/guide/test.html", "guide",
                List.of(), "2026-01-01T00:00:00Z", "", markdown, images, headings);
    }
}
