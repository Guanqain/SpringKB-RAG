package com.guangqian.springkb.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 正文转 Markdown 的代码块提取规则：不同站点的代码高亮结构差异不能导致代码丢失。
 */
class HtmlToMarkdownTest {

    /** chroma 高亮（docs.docker.com）：代码被逐 token 包进多层 span，必须原样还原。 */
    @Test
    void keepsCodeWrappedInNestedSpans() {
        String html = """
                <article><p>Download the image:</p>
                <div class="highlight"><pre tabindex="0" class="chroma"><code class="language-console" data-lang="console"><span class="line"><span class="cl"><span class="gp">$</span> docker pull nginx
                </span></span></code></pre></div>
                </article>""";
        Element root = Jsoup.parse(html).selectFirst("article");

        String markdown = HtmlToMarkdown.convert(root, "https://docs.docker.com/x/").markdown();

        assertThat(markdown).contains("```console");
        assertThat(markdown).contains("$ docker pull nginx");
    }

    /** 普通 pre > code 纯文本结构（mkdocs / vitepress）保持原有行为。 */
    @Test
    void keepsPlainCodeBlock() {
        String html = """
                <article><pre><code class="language-java">int a = 1;
                int b = 2;</code></pre></article>""";
        Element root = Jsoup.parse(html).selectFirst("article");

        String markdown = HtmlToMarkdown.convert(root, "https://example.com/x").markdown();

        assertThat(markdown).contains("```java");
        assertThat(markdown).contains("int a = 1;\nint b = 2;");
    }
}
