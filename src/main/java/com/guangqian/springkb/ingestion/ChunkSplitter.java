package com.guangqian.springkb.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.guangqian.springkb.crawler.HeadingNode;
import com.guangqian.springkb.crawler.ImageRef;
import com.guangqian.springkb.crawler.RawDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 基于标题层级的贪心分块器。
 *
 * 规则摘要：
 * <ul>
 *   <li>按 {@code ^#{1,6} } 识别标题行，用标题栈维护 headingPath（同级替换、更大入栈、更小弹栈）；</li>
 *   <li>连续章节贪心累加打包，达 maxTokens 或即将超限时 flush；</li>
 *   <li>单章节超 maxTokens 时按空行二次切分（代码围栏 ``` 优先整体保留，超过 2×maxTokens 才按预算闭合重开）；仍超限则硬切兜底；</li>
 *   <li>任何块原则上不超过 maxTokens（围栏块 2×maxTokens），超长单行按 token 预算切分；</li>
 *   <li>尾部不足 minTokens 且前面已有块时并入上一块（不超过 maxTokens*1.25），否则独立成块；</li>
 *   <li>整篇不足 minTokens 时单块输出。</li>
 * </ul>
 * token 预算可通过 {@code app.chunking.min-tokens} / {@code app.chunking.max-tokens} 配置。
 */
@Component
@ConfigurationProperties(prefix = "app.chunking")
public class ChunkSplitter {

    private static final Logger log = LoggerFactory.getLogger(ChunkSplitter.class);

    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6} .+");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]*)\\)");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\([^)]*\\)");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]+)`");
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("^\\|.*\\|\\s*$");
    private static final String FENCE = "```";
    /** 行尾换行符的 token 权重（与 TokenEstimator 的 4 字符/token 口径一致）。 */
    private static final double NEWLINE_WEIGHT = 0.25;

    private int minTokens = 500;
    private int maxTokens = 1000;

    public ChunkSplitter() {
    }

    public int getMinTokens() {
        return minTokens;
    }

    public void setMinTokens(int minTokens) {
        this.minTokens = minTokens;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    /** 单个章节（标题行 + 正文行），headingPath 为该章节标题栈快照。 */
    private record Section(String headingLine, int level, List<String> bodyLines, String headingPath) {

        String markdown() {
            StringBuilder sb = new StringBuilder();
            if (headingLine != null) {
                sb.append(headingLine).append('\n');
            }
            for (String line : bodyLines) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    /** 最小不可再分单元（章节或二次切分/硬切后的片段）。 */
    private record Unit(String markdown, String headingPath) {
    }

    /**
     * 将原始文档按标题层级切分为 TextChunk 列表。
     * contentMarkdown 为空/空白时返回空列表。
     */
    public List<TextChunk> split(RawDocument doc) {
        String markdown = doc.contentMarkdown();
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        boolean useHeadings = doc.headings() != null && !doc.headings().isEmpty();
        String fallbackPath = (doc.title() == null || doc.title().isBlank()) ? "root" : doc.title().trim();

        List<Section> sections = parseSections(markdown, useHeadings, fallbackPath);
        List<Unit> units = expand(sections);
        List<TextChunk> chunks = pack(units, doc, fallbackPath);
        mergeTail(chunks);

        List<TextChunk> result = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            TextChunk c = chunks.get(i);
            result.add(new TextChunk(i + 1, c.headingPath(), c.contentMarkdown(),
                    c.contentText(), c.tokenCount(), c.imageRefs()));
        }
        return List.copyOf(result);
    }

    /** 把 markdown 解析为章节列表，同时按标题栈维护每个章节的 headingPath。 */
    private List<Section> parseSections(String markdown, boolean useHeadings, String fallbackPath) {
        List<Section> sections = new ArrayList<>();
        List<String> stack = new ArrayList<>();
        String curHeading = null;
        int curLevel = 0;
        String curPath = fallbackPath;
        List<String> body = new ArrayList<>();

        for (String line : markdown.split("\n", -1)) {
            Matcher m = HEADING_PATTERN.matcher(line);
            if (useHeadings && m.matches()) {
                if (curHeading != null || !body.isEmpty()) {
                    sections.add(new Section(curHeading, curLevel, new ArrayList<>(body), curPath));
                }
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }
                while (stack.size() >= level) {
                    stack.remove(stack.size() - 1);
                }
                stack.add(line.substring(level).trim());
                curHeading = line;
                curLevel = level;
                body.clear();
                curPath = String.join(" > ", stack);
            } else {
                body.add(line);
            }
        }
        if (curHeading != null || !body.isEmpty()) {
            sections.add(new Section(curHeading, curLevel, new ArrayList<>(body), curPath));
        }
        return sections;
    }

    /** 将章节展开为最小单元：超预算章节二次切分/硬切。 */
    private List<Unit> expand(List<Section> sections) {
        List<Unit> units = new ArrayList<>();
        for (Section sec : sections) {
            String md = sec.markdown();
            if (TokenEstimator.estimate(md) <= maxTokens) {
                units.add(new Unit(md, sec.headingPath()));
            } else {
                units.addAll(splitOverlong(md, sec.headingPath()));
            }
        }
        return units;
    }

    /** 二次切分：按空行分段落（代码围栏未闭合的段落与后续合并），再贪心打包成 ≤ maxTokens 的单元。 */
    private List<Unit> splitOverlong(String markdown, String headingPath) {
        List<String> merged = new ArrayList<>();
        boolean fenceOpen = false;
        for (String p : splitByBlankLines(markdown)) {
            if (fenceOpen) {
                int last = merged.size() - 1;
                merged.set(last, merged.get(last) + "\n\n" + p);
                fenceOpen = fenceOpen(merged.get(last));
            } else {
                merged.add(p);
                fenceOpen = fenceOpen(p);
            }
        }

        List<Unit> units = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int curTokens = 0;
        for (String p : merged) {
            int pt = TokenEstimator.estimate(p);
            if (curTokens > 0 && curTokens + pt > maxTokens) {
                units.add(new Unit(cur.toString().trim(), headingPath));
                cur.setLength(0);
                curTokens = 0;
            }
            cur.append(p).append("\n\n");
            curTokens += pt;
        }
        if (cur.length() > 0) {
            units.add(new Unit(cur.toString().trim(), headingPath));
        }

        List<Unit> out = new ArrayList<>();
        for (Unit u : units) {
            if (TokenEstimator.estimate(u.markdown()) > maxTokens) {
                out.addAll(hardSplit(u));
            } else {
                out.add(u);
            }
        }
        return out;
    }

    /**
     * 硬切兜底：按行切分；围栏内的代码块最多保留到 {@link #fenceBudget()}，超过则「闭合当前围栏 + 以同样标记重开」
     * 成对切分（避免半截围栏，也避免单个超大代码块撑爆向量模型输入上限）；超长单行按 token 预算再切。
     */
    private List<Unit> hardSplit(Unit unit) {
        List<Unit> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        double curTokens = 0;
        boolean fence = false;
        String fenceMarker = null;
        for (String line : unit.markdown().split("\n", -1)) {
            String trimmed = line.trim();
            boolean fenceLine = trimmed.startsWith(FENCE);
            if (fenceLine) {
                if (fence) {
                    // 闭合围栏：直接收尾
                    cur.append(line).append('\n');
                    curTokens += TokenEstimator.weightSum(line) + NEWLINE_WEIGHT;
                    fence = false;
                    fenceMarker = null;
                    continue;
                }
                // 开启围栏：当前块已有内容且放不下这一行时先 flush
                if (curTokens > 0 && curTokens + TokenEstimator.weightSum(line) + NEWLINE_WEIGHT > maxTokens) {
                    out.add(new Unit(cur.toString().trim(), unit.headingPath()));
                    cur.setLength(0);
                    curTokens = 0;
                }
                cur.append(line).append('\n');
                curTokens += TokenEstimator.weightSum(line) + NEWLINE_WEIGHT;
                fence = true;
                fenceMarker = trimmed;
                continue;
            }
            List<String> parts = TokenEstimator.estimate(line) > maxTokens ? splitLongLine(line) : List.of(line);
            // 围栏块预留 1 token 给切分时补的闭合围栏，避免落块后超出硬上限
            double budget = fence ? fenceBudget() - 1 : maxTokens;
            for (String part : parts) {
                // 行尾换行符同样计入，否则大量短代码行会系统性低估（每行 ~26 字符 + 换行差 25%）
                double lt = TokenEstimator.weightSum(part) + NEWLINE_WEIGHT;
                if (curTokens > 0 && curTokens + lt > budget) {
                    if (fence) {
                        // 围栏内切分：补一个闭合围栏，再以同样标记重开
                        cur.append(FENCE).append('\n');
                    }
                    out.add(new Unit(cur.toString().trim(), unit.headingPath()));
                    cur.setLength(0);
                    curTokens = 0;
                    if (fence) {
                        cur.append(fenceMarker).append('\n');
                        curTokens = TokenEstimator.weightSum(fenceMarker) + NEWLINE_WEIGHT;
                    }
                }
                cur.append(part).append('\n');
                curTokens += lt;
            }
        }
        if (cur.length() > 0) {
            out.add(new Unit(cur.toString().trim(), unit.headingPath()));
        }
        if (out.isEmpty()) {
            out.add(unit);
        }
        for (Unit u : out) {
            if (TokenEstimator.estimate(u.markdown()) > fenceBudget()) {
                log.warn("硬切后块仍超出预算 tokens={}，请检查围栏/单行切分逻辑", TokenEstimator.estimate(u.markdown()));
            }
        }
        return out;
    }

    /** 围栏块 / 单块硬上限：2×maxTokens。超出则强制切分，保证远低于向量模型单条输入上限。 */
    private int fenceBudget() {
        return maxTokens * 2;
    }

    /** 贪心打包：逐单元累加，超限且已达 minTokens 时 flush；块路径取块首单元的快照。 */
    private List<TextChunk> pack(List<Unit> units, RawDocument doc, String fallbackPath) {
        List<TextChunk> chunks = new ArrayList<>();
        StringBuilder curMd = new StringBuilder();
        String blockPath = null;
        int accTokens = 0;
        for (Unit u : units) {
            int ut = TokenEstimator.estimate(u.markdown());
            boolean overBudget = accTokens > 0 && accTokens + ut > maxTokens && accTokens >= minTokens;
            // 单块硬上限：避免小单元 + 围栏单元合成远超预算的块
            boolean overHardCap = accTokens > 0 && accTokens + ut > fenceBudget();
            if (overBudget || overHardCap) {
                chunks.add(buildChunk(curMd, blockPath != null ? blockPath : fallbackPath, doc));
                curMd.setLength(0);
                accTokens = 0;
                blockPath = null;
            }
            if (blockPath == null) {
                blockPath = u.headingPath();
            }
            curMd.append(u.markdown()).append("\n\n");
            accTokens += ut;
        }
        if (curMd.length() > 0) {
            chunks.add(buildChunk(curMd, blockPath != null ? blockPath : fallbackPath, doc));
        }
        return chunks;
    }

    private TextChunk buildChunk(StringBuilder curMd, String headingPath, RawDocument doc) {
        String md = curMd.toString().trim();
        String text = toText(md);
        return new TextChunk(0, headingPath, md, text,
                TokenEstimator.estimate(text), collectImages(md, doc));
    }

    /** 尾部合并：最后一块 < minTokens 且前面已有块时，并入上一块（合计 ≤ maxTokens*1.25）。 */
    private void mergeTail(List<TextChunk> chunks) {
        if (chunks.size() <= 1) {
            return;
        }
        TextChunk last = chunks.get(chunks.size() - 1);
        if (last.tokenCount() >= minTokens) {
            return;
        }
        TextChunk prev = chunks.get(chunks.size() - 2);
        if (prev.tokenCount() + last.tokenCount() > (int) (maxTokens * 1.25)) {
            return;
        }
        List<ImageRef> images = new ArrayList<>(prev.imageRefs());
        for (ImageRef img : last.imageRefs()) {
            if (images.stream().noneMatch(i -> i.src().equals(img.src()))) {
                images.add(img);
            }
        }
        TextChunk merged = new TextChunk(prev.ordinal(), prev.headingPath(),
                prev.contentMarkdown() + "\n\n" + last.contentMarkdown(),
                prev.contentText() + "\n\n" + last.contentText(),
                prev.tokenCount() + last.tokenCount(), List.copyOf(images));
        chunks.set(chunks.size() - 2, merged);
        chunks.remove(chunks.size() - 1);
    }

    /** Markdown 去语法生成纯文本：代码围栏内容保留但去掉围栏行；行内代码/链接/图片/标题/表格归一化；空行收缩。 */
    static String toText(String markdown) {
        List<String> lines = new ArrayList<>();
        boolean fence = false;
        for (String line : markdown.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                fence = !fence;
                continue;
            }
            if (fence) {
                lines.add(line);
                continue;
            }
            String t = line;
            t = t.replaceFirst("^#{1,6} ", "");
            // 注意：Matcher.replaceAll(Function) 内部走 appendReplacement，会解释返回串中的 '$'/'\'；
            // 内容中的 $refs/$store/\frac 等会触发 "Illegal group reference" / "named capturing group has 0 length name"。
            // 必须用 quoteReplacement 转义（真实 Vue 文档大量含 $ 标识符，见 devlog 2026-10-19/20）。
            t = IMAGE_PATTERN.matcher(t).replaceAll(m -> Matcher.quoteReplacement("[图片] " + m.group(1)));
            t = LINK_PATTERN.matcher(t).replaceAll(m -> Matcher.quoteReplacement(m.group(1)));
            t = INLINE_CODE_PATTERN.matcher(t).replaceAll(m -> Matcher.quoteReplacement(m.group(1)));
            if (TABLE_ROW_PATTERN.matcher(t.trim()).matches()) {
                t = t.trim().replaceAll("^\\||\\|$", "").replaceAll("\\|", " ").replaceAll("\\s+", " ").trim();
            }
            lines.add(t);
        }
        StringBuilder sb = new StringBuilder();
        boolean pendingBlank = false;
        for (String l : lines) {
            if (l.isBlank()) {
                pendingBlank = true;
                continue;
            }
            if (sb.length() > 0) {
                sb.append(pendingBlank ? "\n\n" : "\n");
            }
            sb.append(l);
            pendingBlank = false;
        }
        return sb.toString();
    }

    /** 收集块内图片：优先用 doc.images 匹配 src，匹配不到则从 markdown 解析兜底，按 src 去重。 */
    private List<ImageRef> collectImages(String markdown, RawDocument doc) {
        List<ImageRef> known = doc.images() != null ? doc.images() : List.of();
        List<ImageRef> refs = new ArrayList<>();
        Matcher m = IMAGE_PATTERN.matcher(markdown);
        while (m.find()) {
            String src = m.group(2);
            ImageRef found = known.stream()
                    .filter(i -> src.equals(i.src()))
                    .findFirst()
                    .orElse(new ImageRef(src, m.group(1)));
            if (refs.stream().noneMatch(r -> r.src().equals(found.src()))) {
                refs.add(found);
            }
        }
        return List.copyOf(refs);
    }

    private List<String> splitByBlankLines(String markdown) {
        List<String> paragraphs = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : markdown.split("\n", -1)) {
            if (line.isBlank()) {
                if (cur.length() > 0) {
                    paragraphs.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(line).append('\n');
            }
        }
        if (cur.length() > 0) {
            paragraphs.add(cur.toString());
        }
        return paragraphs;
    }

    /**
     * 超长单行按 token 预算硬切（兜底，可能切断单词但保证可切性）。
     * 按字符权重累加：CJK 1 字符 ≈ 1 token，其余 4 字符 ≈ 1 token；每段 ≤ maxTokens。
     */
    private List<String> splitLongLine(String line) {
        List<String> parts = new ArrayList<>();
        int idx = 0;
        while (idx < line.length()) {
            int start = idx;
            double acc = 0;
            while (idx < line.length()) {
                int codePoint = line.codePointAt(idx);
                double w = TokenEstimator.weight(codePoint);
                if (idx > start && acc + w > maxTokens) {
                    break;
                }
                acc += w;
                idx += Character.charCount(codePoint);
            }
            parts.add(line.substring(start, idx));
        }
        return parts;
    }

    private boolean fenceOpen(String text) {
        int count = 0;
        for (String line : text.split("\n")) {
            if (line.trim().startsWith("```")) {
                count++;
            }
        }
        return count % 2 == 1;
    }
}
