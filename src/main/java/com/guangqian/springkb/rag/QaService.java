package com.guangqian.springkb.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * 用户端版本下拉元数据（公开接口只读查询）：active 文档的去重版本列表，升序。
     * 无用户输入参与拼接，无注入面。
     */
    public List<String> listActiveVersions() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT version FROM kb_document WHERE active = true ORDER BY version",
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
        List<SourceRef> sources = resolveSources(reranked);
        log.info("重排回源：重排后={} 条，回源成功={} 条，准备调用模型", reranked.size(), sources.size());
        if (sources.isEmpty()) {
            // 回源全部失败（理论不出现，kb_chunk 与 vector_store 同事务写入）；按兜底处理
            log.warn("回源失败：初检命中但 kb_chunk/kb_document 无对应行，按兜底处理 question={}", question);
            return new QaResult(false, NO_ANSWER, List.of());
        }

        String answer = generate(question, version, sources);
        return new QaResult(true, answer, sources);
    }

    /** 初检候选最高相似度（score 为 null 按 0 处理）。 */
    private static double topScore(List<Document> candidates) {
        Double score = candidates.get(0).getScore();
        return score == null ? 0.0d : score;
    }

    /**
     * 按 chunk id 批量回源 kb_chunk JOIN kb_document（§6.5）；查不到的行跳过（引用越界保护）。
     * sources 顺序与 reranked（送模型）顺序一致，index 从 1..n。
     */
    private List<SourceRef> resolveSources(List<Document> reranked) {
        if (reranked.isEmpty()) {
            return List.of();
        }
        Map<String, SourceRow> byId = querySourceRows(reranked.stream().map(Document::getId).toList());
        List<SourceRef> sources = new ArrayList<>(reranked.size());
        int index = 1;
        for (Document doc : reranked) {
            SourceRow row = byId.get(doc.getId());
            if (row == null) {
                continue;
            }
            Double score = doc.getScore();
            sources.add(new SourceRef(index++, row.authority(), row.version(), row.title(),
                    row.section(), row.headingPath(), score == null ? 0.0d : score, truncate(row.contentMarkdown(), SNIPPET_MAX_CHARS)));
        }
        return sources;
    }

    private Map<String, SourceRow> querySourceRows(List<String> ids) {
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = """
                SELECT c.id, c.content_markdown, c.heading_path, d.title, d.version, d.section, d.authority
                FROM kb_chunk c JOIN kb_document d ON d.id = c.doc_id
                WHERE c.id IN (%s)
                """.formatted(placeholders);
        Map<String, SourceRow> rows = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            rows.put(rs.getString("id"), new SourceRow(
                    rs.getString("id"),
                    rs.getString("content_markdown"),
                    rs.getString("heading_path"),
                    rs.getString("title"),
                    rs.getString("version"),
                    rs.getString("section"),
                    rs.getString("authority")));
        }, ids.toArray());
        return rows;
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
        sys.append("4. 如果【检索内容】不足以回答用户问题，明确回答\"知识库中未找到相关答案\"；\n");
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

    /** 回源查询行（内部值对象）。 */
    private record SourceRow(String id, String contentMarkdown, String headingPath,
                             String title, String version, String section, String authority) {
    }
}
