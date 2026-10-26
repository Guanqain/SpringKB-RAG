package com.guangqian.springkb.crawler;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * HTML 正文 → 结构化 Markdown 转换器。
 * 保留：标题层级(H1~H6)、代码块（围栏+语言）、表格、图片 alt、列表、链接。
 * 剔除：script/style/nav/header/footer/aside 等导航噪声。
 * 产出 headings 与 images 两个结构化列表，供后续按标题分块与图片处理。
 */
public final class HtmlToMarkdown {

    public record Result(String markdown, List<ImageRef> images, List<HeadingNode> headings) {
    }

    private static final Set<String> NOISE = Set.of(
            "script", "style", "nav", "header", "footer", "aside",
            "form", "iframe", "svg", "noscript", "template", "button");

    private HtmlToMarkdown() {
    }

    public static Result convert(Element root, String baseUrl) {
        StringBuilder sb = new StringBuilder();
        List<ImageRef> images = new ArrayList<>();
        List<HeadingNode> headings = new ArrayList<>();
        walkChildren(root, sb, images, headings, baseUrl, 0);
        return new Result(sb.toString().trim(), images, headings);
    }

    // ---------- 遍历 ----------

    private static void walkChildren(Element el, StringBuilder sb, List<ImageRef> images,
                                     List<HeadingNode> headings, String baseUrl, int listLevel) {
        for (Node child : el.childNodes()) {
            if (child instanceof TextNode tn) {
                sb.append(listLevel > 0 ? tn.getWholeText() : collapse(tn.getWholeText()));
            } else if (child instanceof Element c) {
                emit(c, sb, images, headings, baseUrl, listLevel);
            }
        }
    }

    private static void emit(Element el, StringBuilder sb, List<ImageRef> images,
                             List<HeadingNode> headings, String baseUrl, int listLevel) {
        String tag = el.tagName();
        if (NOISE.contains(tag)) {
            return;
        }
        switch (tag) {
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                int level = tag.charAt(1) - '0';
                String text = inlineText(el);
                headings.add(new HeadingNode(level, text, el.id()));
                nl(sb);
                sb.append("#".repeat(level)).append(' ').append(escapeInline(text)).append("\n\n");
            }
            case "pre" -> {
                nl(sb);
                sb.append("```").append(langOf(el)).append('\n').append(codeText(el)).append("\n```\n\n");
            }
            case "img" -> {
                String src = el.absUrl("src");
                String alt = el.attr("alt");
                images.add(new ImageRef(src, alt));
                nl(sb);
                sb.append("![").append(escapeInline(alt)).append("](").append(src).append(")\n\n");
            }
            case "table" -> {
                nl(sb);
                sb.append(tableToMd(el)).append("\n\n");
            }
            case "ul" -> {
                nl(sb);
                int i = 1;
                for (Element li : el.children()) {
                    if ("li".equals(li.tagName())) {
                        sb.append("- ").append(inlineBlock(li, images, headings, baseUrl)).append('\n');
                    }
                }
                sb.append('\n');
            }
            case "ol" -> {
                nl(sb);
                int i = 1;
                for (Element li : el.children()) {
                    if ("li".equals(li.tagName())) {
                        sb.append(i++).append(". ").append(inlineBlock(li, images, headings, baseUrl)).append('\n');
                    }
                }
                sb.append('\n');
            }
            case "blockquote" -> {
                nl(sb);
                sb.append("> ").append(escapeInline(inlineText(el))).append("\n\n");
            }
            case "br" -> sb.append('\n');
            case "hr" -> {
                nl(sb);
                sb.append("---\n\n");
            }
            case "a" -> {
                String href = el.absUrl("href");
                String text = inlineText(el);
                if (href.isEmpty()) {
                    sb.append(escapeInline(text));
                } else {
                    sb.append('[').append(escapeInline(text)).append("](").append(href).append(')');
                }
            }
            case "p", "div", "section", "article", "main", "li", "td", "th", "dt", "dd" -> {
                walkChildren(el, sb, images, headings, baseUrl, listLevel);
            }
            default -> walkChildren(el, sb, images, headings, baseUrl, listLevel);
        }
    }

    // ---------- 内联文本 ----------

    /** 递归收集内联纯文本：img 取其 alt；code 保留反引号；压缩空白 */
    private static String inlineText(Element el) {
        StringBuilder sb = new StringBuilder();
        for (Node n : el.childNodes()) {
            if (n instanceof TextNode tn) {
                sb.append(tn.getWholeText());
            } else if (n instanceof Element c) {
                String t = c.tagName();
                if (NOISE.contains(t)) {
                    continue;
                }
                if ("img".equals(t)) {
                    sb.append(c.attr("alt"));
                } else if ("br".equals(t)) {
                    sb.append(' ');
                } else if ("code".equals(t)) {
                    sb.append('`').append(c.text()).append('`');
                } else {
                    sb.append(inlineText(c));
                }
            }
        }
        return collapse(sb.toString());
    }

    /** li 内容可能含嵌套块（嵌套 ul/ol/图片等），走完整 emit 以保结构 */
    private static String inlineBlock(Element li, List<ImageRef> images, List<HeadingNode> headings, String baseUrl) {
        StringBuilder sb = new StringBuilder();
        for (Node n : li.childNodes()) {
            if (n instanceof TextNode tn) {
                sb.append(tn.getWholeText());
            } else if (n instanceof Element c) {
                String t = c.tagName();
                if ("ul".equals(t) || "ol".equals(t)) {
                    // 嵌套列表：缩进简化处理，直接递归其 li
                    for (Element sub : c.children()) {
                        if ("li".equals(sub.tagName())) {
                            sb.append("  - ").append(inlineBlock(sub, images, headings, baseUrl)).append('\n');
                        }
                    }
                } else if ("img".equals(t)) {
                    String src = c.absUrl("src");
                    String alt = c.attr("alt");
                    images.add(new ImageRef(src, alt));
                    sb.append("![").append(escapeInline(alt)).append("](").append(src).append(')');
                } else {
                    sb.append(inlineText(c));
                }
            }
        }
        return collapse(sb.toString());
    }

    // ---------- 代码块 ----------

    private static String langOf(Element pre) {
        Element code = pre.selectFirst("code");
        if (code == null) {
            return "";
        }
        for (String cls : code.classNames()) {
            if (cls.startsWith("language-")) {
                return cls.substring("language-".length());
            }
        }
        String dataLang = code.attr("data-lang");
        return dataLang.isEmpty() ? "" : dataLang;
    }

    /**
     * 保留代码块原始换行与缩进（Element.text() 会压平，不能用）。
     * 递归收集所有后代文本节点，兼容 chroma/prism 等把代码逐 token 包进 span 的站点。
     */
    private static String codeText(Element pre) {
        StringBuilder sb = new StringBuilder();
        collectCodeText(pre, sb);
        return sb.toString().trim();
    }

    private static void collectCodeText(Node node, StringBuilder sb) {
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode tn) {
                sb.append(tn.getWholeText());
            } else if (child instanceof Element c) {
                collectCodeText(c, sb);
            }
        }
    }

    // ---------- 表格 ----------

    private static String tableToMd(Element table) {
        List<List<String>> rows = new ArrayList<>();
        for (Element tr : table.select("tr")) {
            List<String> cells = new ArrayList<>();
            for (Element td : tr.select("th, td")) {
                cells.add(escapeCell(inlineText(td)));
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        if (rows.isEmpty()) {
            return "";
        }
        int cols = rows.get(0).size();
        StringBuilder sb = new StringBuilder();
        sb.append("| ").append(String.join(" | ", pad(rows.get(0), cols))).append(" |\n");
        List<String> sep = new ArrayList<>();
        for (int i = 0; i < cols; i++) {
            sep.add("---");
        }
        sb.append("| ").append(String.join(" | ", sep)).append(" |\n");
        for (int i = 1; i < rows.size(); i++) {
            sb.append("| ").append(String.join(" | ", pad(rows.get(i), cols))).append(" |\n");
        }
        return sb.toString();
    }

    private static List<String> pad(List<String> row, int cols) {
        while (row.size() < cols) {
            row.add("");
        }
        return row;
    }

    // ---------- 小工具 ----------

    private static void nl(StringBuilder sb) {
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
    }

    /** 空白压缩（非代码场景） */
    private static String collapse(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    /** 转义 Markdown 内联特殊符号：* _ [ ] 开头字符 */
    private static String escapeInline(String s) {
        return s.replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("[", "\\[")
                .replace("]", "\\]");
    }

    /** 表格单元格：转义管道符，压平换行 */
    private static String escapeCell(String s) {
        return s.replace("|", "\\|").replace("\n", " ");
    }
}
