package com.guangqian.springkb.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.guangqian.springkb.common.OriginalUrlResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 检索增强问答（能力三，§6.1/§6.4/§6.5）。
 *
 * <p>ask 主流程：初检 topK×3 → 兜底判定（最高相似度 &lt; threshold → 不调模型直接返回）→
 * 权威性加权重排取前 topK → 按 chunk id 回源 kb_chunk/kb_document 组装 sources →
 * 组装 prompt（引用编号 [1]..[n] + 来源标注）→ ChatClient 生成。</p>
 *
 * <p>兜底（§6.4）：初检为空或最高相似度低于 {@code app.rag.similarity-threshold}（默认 0.65）
 * 时返回 found=false 与「知识库中未找到相关答案」，绝不调用 Chat 模型。</p>
 *
 * <p>引用相关性：只有聚合后 maxScore ≥ 同一阈值的来源才会进入 sources 与 prompt，
 * 避免「第 1 条勉强过阈值、其余弱相关 chunk 全部展示」。若模型自身判定无法回答
 * （输出包含 {@link #NO_ANSWER}），同样返回 found=false 且 sources 为空，不展示无意义引用。</p>
 *
 * <p>引用溯源（§6.5）：sources 顺序与送模型的引用编号一一对应；回源按 chunk id 批量查询，
 * 查不到的行跳过（引用越界保护）；越界引用由 prompt 约束 + sources 权威列表兜住，不做字符串后处理。</p>
 */
@Service
public class QaService {

    private static final Logger log = LoggerFactory.getLogger(QaService.class);

    /** 兜底答案（§5.2）。 */
    public static final String NO_ANSWER = "知识库中未找到相关答案";

    /** snippet（原文片段）截断长度。 */
    private static final int SNIPPET_MAX_CHARS = 800;

    private final RagSearchService ragSearchService;
    private final ChatClient chatClient;
    private final JdbcTemplate jdbcTemplate;
    private final double similarityThreshold;

    public QaService(RagSearchService ragSearchService, ChatClient chatClient,
            JdbcTemplate jdbcTemplate, @Value("${app.rag.similarity-threshold:0.65}") double similarityThreshold) {
        this.ragSearchService = ragSearchService;
        this.chatClient = chatClient;
        this.jdbcTemplate = jdbcTemplate;
        this.similarityThreshold = similarityThreshold;
    }

    /**
     * 用户端版本下拉元数据（公开接口只读查询）：team 可见 + active 文档的去重版本列表，升序。
     * 只统计 team 可见文档，避免 private 文档的存在性通过版本列表泄漏。
     */
    public List<String> listActiveVersions() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT version FROM kb_document WHERE active = true AND visibility = 'team' ORDER BY version",
                String.class);
    }

    /**
     * 检索增强问答（§6.1）：version 非法时抛 IllegalArgumentException（控制器映射 400）。
     */
    public QaResult ask(String question, String version, int topK) {
        int k = Math.max(1, topK);
        List<Document> candidates = ragSearchService.searchDocuments(question, version, Math.max(1, k * 3));
        log.info("检索初检：question={} version={} topK={} 候选={} topScore={}",
                question, version, k * 3, candidates.size(),
                candidates.isEmpty() ? "-" : topScore(candidates));
        if (candidates.isEmpty() || topScore(candidates) < similarityThreshold) {
            log.info("兜底命中：初检 {} 条，最高相似度 {} < 阈值 {}，不调用模型 question={} version={}",
                    candidates.size(), candidates.isEmpty() ? "-" : topScore(candidates), similarityThreshold, question, version);
            return new QaResult(false, NO_ANSWER, List.of());
        }

        List<Document> reranked = ragSearchService.rerankAndTop(candidates, k);
        List<SourceRef> sources = resolveSources(reranked, similarityThreshold);
        log.info("重排回源：重排后={} 条，回源且达标={} 条，准备调用模型", reranked.size(), sources.size());
        if (sources.isEmpty()) {
            // 回源全部失败，或重排后没有任何来源达到阈值（仅第 1 条勉强过线）；按兜底处理
            log.info("来源不达标：初检命中但无来源相似度 ≥ 阈值 {}，按兜底处理 question={}", similarityThreshold, question);
            return new QaResult(false, NO_ANSWER, List.of());
        }

        String answer = generate(question, version, sources);
        if (answer == null || answer.contains(NO_ANSWER)) {
            // 模型依据【检索内容】判定无法回答（含内容与问题无关）：不展示无意义引用
            log.info("模型判定无法回答：question={} version={}", question, version);
            return new QaResult(false, NO_ANSWER, List.of());
        }
        return new QaResult(true, answer, sources);
    }

    /** 初检候选最高相似度（score 为 null 按 0 处理）。 */
    private static double topScore(List<Document> candidates) {
        Double score = candidates.get(0).getScore();
        return score == null ? 0.0d : score;
    }

    /**
     * 按 chunk id 批量回源 kb_chunk JOIN kb_document（§6.5），再按文档级聚合（C6）：
     * 同一文档的多个 chunk 合并为一条来源，[n] 角标按文档编号（保留 reranked 首次出现顺序）；
     * 查不到的行跳过（引用越界保护）；聚合后 maxScore 低于 {@code minScore} 的文档不计入来源，
     * 避免弱相关 chunk 也变成引用。
     */
    private List<SourceRef> resolveSources(List<Document> reranked, double minScore) {
        if (reranked.isEmpty()) {
            return List.of();
        }
        Map<String, SourceRow> byId = querySourceRows(reranked.stream().map(Document::getId).toList());
        Map<String, DocGroup> groups = new LinkedHashMap<>();
        for (Document doc : reranked) {
            SourceRow row = byId.get(doc.getId());
            if (row == null) {
                continue;
            }
            double score = doc.getScore() == null ? 0.0d : doc.getScore();
            DocGroup group = groups.get(row.docId());
            if (group == null) {
                groups.put(row.docId(), new DocGroup(row, score));
            } else {
                group.absorb(row, score);
            }
        }
        List<SourceRef> sources = new ArrayList<>(groups.size());
        int index = 1;
        for (DocGroup group : groups.values()) {
            if (group.maxScore() < minScore) {
                continue;
            }
            sources.add(new SourceRef(index++, group.authority(), group.version(), group.title(),
                    group.section(), group.headingPath(), group.maxScore(),
                    group.docId(), group.originalUrl(), truncate(group.combinedMarkdown(), SNIPPET_MAX_CHARS)));
        }
        return sources;
    }

    private Map<String, SourceRow> querySourceRows(List<String> ids) {
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = """
                SELECT c.id, c.doc_id, c.content_markdown, c.heading_path,
                       d.title, d.version, d.section, d.authority, d.product, d.origin, d.doc_key
                FROM kb_chunk c JOIN kb_document d ON d.id = c.doc_id
                WHERE c.id IN (%s) AND d.visibility = 'team'
                """.formatted(placeholders);
        Map<String, SourceRow> rows = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            String version = rs.getString("version");
            rows.put(rs.getString("id"), new SourceRow(
                    rs.getString("id"),
                    rs.getString("doc_id"),
                    rs.getString("content_markdown"),
                    rs.getString("heading_path"),
                    rs.getString("title"),
                    version,
                    rs.getString("section"),
                    rs.getString("authority"),
                    OriginalUrlResolver.resolve(
                            rs.getString("product"), rs.getString("origin"), version, rs.getString("doc_key"))));
        }, ids.toArray());
        return rows;
    }

    /** 文档级聚合并集：同一文档多个 chunk 的 markdown 以空行拼接，score 取最高、headingPath 随最高分块。 */
    private static final class DocGroup {

        private final String docId;
        private final String authority;
        private final String version;
        private final String title;
        private final String section;
        private final String originalUrl;
        private double maxScore;
        private String headingPath;
        private final StringBuilder markdown = new StringBuilder();

        DocGroup(SourceRow row, double score) {
            this.docId = row.docId();
            this.authority = row.authority();
            this.version = row.version();
            this.title = row.title();
            this.section = row.section();
            this.originalUrl = row.originalUrl();
            this.maxScore = score;
            this.headingPath = row.headingPath();
            this.markdown.append(row.contentMarkdown());
        }

        void absorb(SourceRow row, double score) {
            if (score > maxScore) {
                maxScore = score;
                headingPath = row.headingPath();
            }
            markdown.append("\n\n").append(row.contentMarkdown());
        }

        String combinedMarkdown() {
            return markdown.toString();
        }

        String docId() {
            return docId;
        }

        String authority() {
            return authority;
        }

        String version() {
            return version;
        }

        String title() {
            return title;
        }

        String section() {
            return section;
        }

        String originalUrl() {
            return originalUrl;
        }

        double maxScore() {
            return maxScore;
        }

        String headingPath() {
            return headingPath;
        }
    }

    private String generate(String question, String version, List<SourceRef> sources) {
        Prompt prompt = buildPrompt(question, version, sources);
        try {
            return chatClient.prompt(prompt).call().content();
        } catch (RuntimeException e) {
            // 网络不通/超时等外部故障：转成明确的领域异常，由控制器映射 503，不混同为检索兜底
            log.error("模型调用失败：question={} version={} 原因={}", question, version, e.getMessage(), e);
            throw new ModelUnavailableException("模型服务暂时不可用", e);
        }
    }

    /** 组装 prompt：System 消息强制引用规则，User 消息为【问题】+【检索内容】（编号与 sources 一致）。 */
    private Prompt buildPrompt(String question, String version, List<SourceRef> sources) {
        StringBuilder sys = new StringBuilder();
        sys.append("你是 Spring 知识库助手。请严格遵守以下规则回答用户的问题：\n");
        sys.append("1. 只依据下方【检索内容】回答，不得编造，不得使用检索内容之外的知识；\n");
        sys.append("2. 每个关键结论后标注引用编号 [1]..[").append(sources.size())
                .append("]，编号必须来自下方给定的内容编号，禁止使用越界编号；\n");
        sys.append("3. 引用时标明来源类型：（官方）或（社区），取自检索内容中每条标注的权威性；\n");
        sys.append("4. 如果【检索内容】不足以回答用户问题（包括检索内容与问题主题无关、仅字面相似），"
                + "只回答\"知识库中未找到相关答案\"，不要引用任何内容；\n");
        if (version == null || version.isBlank()) {
            sys.append("5. 未指定版本：若检索内容中存在版本差异，请在回答中说明版本差异。");
        } else {
            sys.append("5. 指定版本 ").append(version).append("：仅使用该版本的检索内容。");
        }

        StringBuilder user = new StringBuilder();
        user.append("【问题】").append(question).append("\n");
        user.append("【检索内容】\n");
        for (SourceRef s : sources) {
            String kind = "official".equals(s.source()) ? "官方" : "社区";
            user.append("[").append(s.index()).append("] （").append(kind)
                    .append("）version=").append(s.version()).append(" title=").append(s.title()).append("\n");
            user.append(s.snippet()).append("\n");
        }
        return new Prompt(List.of(new SystemMessage(sys.toString()), new UserMessage(user.toString())));
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    /** 回源查询行（内部值对象）；originalUrl 已按 product/origin/version/doc_key 重建，可能为 null。 */
    private record SourceRow(String id, String docId, String contentMarkdown, String headingPath,
                             String title, String version, String section, String authority,
                             String originalUrl) {
    }
}
