package com.guangqian.springkb.eval;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.guangqian.springkb.rag.RagSearchService;
import com.guangqian.springkb.rag.Reranker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 检索基线评测入口：`--baseline` 激活（同 ImportRunner/CrawlerRunner 模式，无参跳过）。
 *
 * <p>流程：读活跃语料 → 固定种子分层抽样 → 生成合成自检索查询 + 加载精选真实问句
 * （resources/eval/queries.json）→ 开放版本 + 锁定版本两组检索 → BaselineEvaluator 计算
 * 四维指标 → 结果 JSON 落盘 data/baseline/result-&lt;ts&gt;.json + 写 docs/检索基线测评报告.md。</p>
 *
 * <p>可用参数：{@code --baseline-sample=N}（合成抽样数，默认 120）、
 * {@code --baseline-topk=N}（TopK，默认 10，IR 同时统计 5/10 两档）。</p>
 */
@Component
public class BaselineRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BaselineRunner.class);

    private static final int DEFAULT_SAMPLE = 120;
    private static final int DEFAULT_TOP_K = 10;
    private static final long SEED = 42L;

    private final RagSearchService ragSearchService;
    private final Reranker reranker;
    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;
    private final double threshold;

    public BaselineRunner(RagSearchService ragSearchService, Reranker reranker,
            JdbcTemplate jdbcTemplate, JsonMapper jsonMapper,
            @Value("${app.rag.similarity-threshold:0.65}") double threshold) {
        this.ragSearchService = ragSearchService;
        this.reranker = reranker;
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
        this.threshold = threshold;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (args.getOptionValues("baseline") == null) {
            return;
        }
        int sampleSize = resolveInt(args, "baseline-sample", DEFAULT_SAMPLE);
        int topK = resolveInt(args, "baseline-topk", DEFAULT_TOP_K);

        List<CorpusDoc> corpus = loadCorpus();
        log.info("[baseline] 语料活跃文档={} 抽样={} topK={} threshold={} boost={}",
                corpus.size(), sampleSize, topK, threshold, reranker.getBoost());

        List<CorpusDoc> sampled = sampleStratified(corpus, sampleSize, new Random(SEED));
        List<CuratedQuery> curated = loadCurated();
        Map<String, List<String>> titleToIds = indexByTitle(corpus);

        List<Map<String, Object>> runs = new ArrayList<>();
        List<List<String>> synRetrieved = new ArrayList<>();
        List<Set<String>> synRelevant = new ArrayList<>();
        List<Double> lockedHit = new ArrayList<>();
        List<Boolean> lockedLeaks = new ArrayList<>();
        List<List<String>> curRetrieved = new ArrayList<>();
        List<Set<String>> curRelevant = new ArrayList<>();
        List<Double> answerableTopScores = new ArrayList<>();
        List<Boolean> negativeFallbackCorrect = new ArrayList<>();
        List<Double> rerankShifts = new ArrayList<>();
        int rerankOfficialQueries = 0;

        // ---- 合成自检索（开放版本：IR 指标 + 重排 + 兜底）----
        for (CorpusDoc doc : sampled) {
            String query = buildSyntheticQuery(doc);
            SearchOutcome out = runSearch(query, null, topK);
            Set<String> relevant = Set.of(doc.id());
            runs.add(toRun("synthetic-open", doc.product(), doc.authority(), query, null,
                    doc.id(), out, relevant, rankOf(out.retrieved(), doc.id()), false));
            synRetrieved.add(out.retrieved());
            synRelevant.add(relevant);
            answerableTopScores.add(out.topScore());
            if ("official".equals(doc.authority()) && out.rawOrder().contains(doc.id())) {
                double shift = out.rawOrder().indexOf(doc.id()) - out.rerankedOrder().indexOf(doc.id());
                rerankShifts.add(shift);
                rerankOfficialQueries++;
            }
        }

        // ---- 合成自检索（锁定版本：版本过滤正确性）----
        for (CorpusDoc doc : sampled) {
            String query = buildSyntheticQuery(doc);
            SearchOutcome out = runSearch(query, doc.version(), topK);
            boolean leak = out.retrievedVersions().stream().anyMatch(v -> !doc.version().equals(v));
            boolean hit = out.retrieved().contains(doc.id());
            runs.add(toRun("synthetic-version-locked", doc.product(), doc.authority(), query,
                    doc.version(), doc.id(), out, Set.of(doc.id()),
                    rankOf(out.retrieved(), doc.id()), leak));
            lockedHit.add(hit ? 1.0 : 0.0);
            lockedLeaks.add(leak);
        }

        // ---- 精选真实问句 ----
        int curatedSkipped = 0;
        for (CuratedQuery cq : curated) {
            Set<String> relevant = resolveRelevant(cq, titleToIds);
            if (relevant.isEmpty() && !cq.relevantTitles().isEmpty()) {
                curatedSkipped++;
                log.warn("[baseline] 精选查询 {} 相关标题未解析到 doc_id，跳过: {}", cq.id(), cq.relevantTitles());
                continue;
            }
            SearchOutcome out = runSearch(cq.query(), cq.version(), topK);
            boolean leak = cq.version() != null
                    && out.retrievedVersions().stream().anyMatch(v -> !cq.version().equals(v));
            runs.add(toRun("curated", cq.category(), "", cq.query(), cq.version(), null,
                    out, relevant, rankOfAny(out.retrieved(), relevant), leak));
            if (!relevant.isEmpty()) {
                curRetrieved.add(out.retrieved());
                curRelevant.add(relevant);
                answerableTopScores.add(out.topScore());
            } else {
                negativeFallbackCorrect.add(out.topScore() < threshold);
            }
        }

        // ---- 汇总 + 落盘 ----
        Map<String, Object> summary = buildSummary(topK,
                synRetrieved, synRelevant, lockedHit, lockedLeaks,
                curRetrieved, curRelevant, curatedSkipped, answerableTopScores,
                negativeFallbackCorrect, rerankShifts, rerankOfficialQueries);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        root.put("config", Map.of("sample", sampleSize, "topK", topK,
                "similarityThreshold", threshold, "boost", reranker.getBoost(), "seed", SEED));
        root.put("corpus", Map.of("activeDocs", corpus.size(), "sampledDocs", sampled.size(),
                "curatedQueries", curated.size(), "curatedSkipped", curatedSkipped));
        root.put("summary", summary);
        root.put("runs", runs);

        Path outDir = Path.of("data", "baseline");
        Files.createDirectories(outDir);
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path jsonPath = outDir.resolve("result-" + ts + ".json");
        Files.writeString(jsonPath, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                StandardCharsets.UTF_8);

        Path reportPath = Path.of("docs", "检索基线测评报告.md");
        Files.writeString(reportPath, renderReport(root), StandardCharsets.UTF_8);

        log.info("[baseline] 完成 结果JSON={} 报告={}", jsonPath.toAbsolutePath(), reportPath.toAbsolutePath());
    }

    // ---------- 语料 / 抽样 / 查询构造 ----------

    private List<CorpusDoc> loadCorpus() {
        String sql = """
                SELECT d.id, d.product, d.title, d.version, d.authority,
                       c.heading_path, c.content_text
                FROM kb_document d
                LEFT JOIN kb_chunk c ON c.id = (
                    SELECT c2.id FROM kb_chunk c2 WHERE c2.doc_id = d.id ORDER BY c2.ordinal LIMIT 1
                )
                WHERE d.active = true
                ORDER BY d.id
                """;
        return jdbcTemplate.query(sql, rs -> {
            List<CorpusDoc> list = new ArrayList<>();
            while (rs.next()) {
                list.add(new CorpusDoc(
                        rs.getString("id"), rs.getString("product"), rs.getString("title"),
                        rs.getString("version"), rs.getString("authority"),
                        rs.getString("heading_path"), rs.getString("content_text")));
            }
            return list;
        });
    }

    private static List<CorpusDoc> sampleStratified(List<CorpusDoc> corpus, int sampleSize, Random random) {
        Map<String, List<CorpusDoc>> groups = new LinkedHashMap<>();
        for (CorpusDoc d : corpus) {
            String key = d.product() + "|" + d.authority() + "|" + d.version();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }
        for (List<CorpusDoc> g : groups.values()) {
            Collections.shuffle(g, random);
        }
        List<String> keys = new ArrayList<>(groups.keySet());
        List<CorpusDoc> out = new ArrayList<>();
        int round = 0;
        while (out.size() < sampleSize) {
            boolean added = false;
            for (String key : keys) {
                List<CorpusDoc> g = groups.get(key);
                if (round < g.size() && out.size() < sampleSize) {
                    out.add(g.get(round));
                    added = true;
                }
            }
            if (!added) {
                break;
            }
            round++;
        }
        return out;
    }

    private static String buildSyntheticQuery(CorpusDoc doc) {
        StringBuilder q = new StringBuilder(doc.title() == null ? "" : doc.title());
        String heading = lastHeadingSegment(doc.headingPath());
        if (heading != null && !heading.isBlank() && !heading.equalsIgnoreCase(doc.title())) {
            q.append(' ').append(heading);
        }
        String words = firstWords(doc.contentText(), 20);
        if (words != null && !words.isBlank()) {
            q.append(' ').append(words);
        }
        return q.toString().trim().isEmpty() ? String.valueOf(doc.title()) : q.toString().trim();
    }

    private static String lastHeadingSegment(String headingPath) {
        if (headingPath == null || headingPath.isBlank()) {
            return "";
        }
        String[] parts = headingPath.split(">");
        return parts[parts.length - 1].trim();
    }

    private static String firstWords(String text, int n) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] words = text.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n && i < words.length; i++) {
            sb.append(words[i]).append(' ');
        }
        return sb.toString().trim();
    }

    private static Map<String, List<String>> indexByTitle(List<CorpusDoc> corpus) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (CorpusDoc d : corpus) {
            map.computeIfAbsent(d.title(), k -> new ArrayList<>()).add(d.id());
        }
        return map;
    }

    private static Set<String> resolveRelevant(CuratedQuery cq, Map<String, List<String>> titleToIds) {
        Set<String> ids = new LinkedHashSet<>();
        for (String title : cq.relevantTitles()) {
            List<String> matched = titleToIds.get(title);
            if (matched != null) {
                ids.addAll(matched);
            }
        }
        return ids;
    }

    // ---------- 检索执行 ----------

    /** 检索一次：初检 topK×3 → 重排取 topK；返回系统输出、原始序、重排序与 topScore（初检首条原始分）。 */
    private SearchOutcome runSearch(String query, String version, int topK) {
        int initialTopK = Math.max(1, topK * 3);
        List<Document> raw = ragSearchService.searchDocuments(query, version, initialTopK);
        List<Document> reranked = ragSearchService.rerankAndTop(raw, topK);
        double topScore = raw.isEmpty() ? 0.0d : scoreOf(raw.get(0));
        return new SearchOutcome(
                docIds(reranked), versionsOf(reranked), topScore,
                docIds(raw), docIds(reranked));
    }

    private static List<String> docIds(List<Document> docs) {
        List<String> ids = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Document d : docs) {
            String docId = docIdOf(d);
            if (docId != null && seen.add(docId)) {
                ids.add(docId);
            }
        }
        return ids;
    }

    private static List<String> versionsOf(List<Document> docs) {
        List<String> vs = new ArrayList<>();
        for (Document d : docs) {
            Object v = d.getMetadata().get("version");
            vs.add(v == null ? "" : String.valueOf(v));
        }
        return vs;
    }

    private static String docIdOf(Document d) {
        Object o = d.getMetadata().get("doc_id");
        return o == null ? d.getId() : String.valueOf(o);
    }

    private static double scoreOf(Document d) {
        Double s = d.getScore();
        return s == null ? 0.0d : s;
    }

    private static int rankOf(List<String> retrieved, String target) {
        return retrieved.indexOf(target) + 1;
    }

    private static int rankOfAny(List<String> retrieved, Set<String> relevant) {
        for (int i = 0; i < retrieved.size(); i++) {
            if (relevant.contains(retrieved.get(i))) {
                return i + 1;
            }
        }
        return -1;
    }

    private static Map<String, Object> toRun(String kind, String product, String authority,
            String query, String version, String targetDocId, SearchOutcome out,
            Set<String> relevant, int hitRank, boolean leaked) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        m.put("product", product);
        m.put("authority", authority);
        m.put("query", query);
        m.put("version", version);
        m.put("targetDocId", targetDocId);
        m.put("hitRank", hitRank);
        m.put("leaked", leaked);
        m.put("topScore", round4(out.topScore()));
        m.put("retrieved", out.retrieved());
        m.put("retrievedVersions", out.retrievedVersions());
        m.put("rawOrder", out.rawOrder());
        m.put("rerankedOrder", out.rerankedOrder());
        return m;
    }

    // ---------- 汇总 ----------

    private Map<String, Object> buildSummary(int topK,
            List<List<String>> synRetrieved, List<Set<String>> synRelevant,
            List<Double> lockedHit, List<Boolean> lockedLeaks,
            List<List<String>> curRetrieved, List<Set<String>> curRelevant,
            int curatedSkipped, List<Double> answerableTopScores, List<Boolean> negativeFallbackCorrect,
            List<Double> rerankShifts, int rerankOfficialQueries) {

        Map<String, Object> s = new LinkedHashMap<>();

        s.put("synthetic_open", irSummary(synRetrieved, synRelevant, topK));

        Map<String, Object> locked = new LinkedHashMap<>();
        locked.put("count", lockedHit.size());
        locked.put("targetHit", round4(BaselineEvaluator.mean(lockedHit)));
        locked.put("leakRate", round4(BaselineEvaluator.versionLeakRate(lockedLeaks)));
        long correct = 0;
        for (int i = 0; i < lockedHit.size(); i++) {
            if (lockedHit.get(i) > 0 && !lockedLeaks.get(i)) {
                correct++;
            }
        }
        locked.put("correctness", lockedHit.isEmpty() ? 0.0
                : round4((double) correct / lockedHit.size()));
        s.put("synthetic_version_locked", locked);

        Map<String, Object> cur = irSummary(curRetrieved, curRelevant, topK);
        cur.put("skipped", curatedSkipped);
        s.put("curated", cur);

        Map<String, Object> fb = new LinkedHashMap<>();
        fb.put("rate", round4(BaselineEvaluator.fallbackRate(answerableTopScores, threshold)));
        fb.put("threshold", threshold);
        List<Double> sorted = new ArrayList<>(answerableTopScores);
        Collections.sort(sorted);
        fb.put("minTopScore", round4(sorted.isEmpty() ? 0.0 : sorted.get(0)));
        fb.put("medianTopScore", round4(median(sorted)));
        fb.put("maxTopScore", round4(sorted.isEmpty() ? 0.0 : sorted.get(sorted.size() - 1)));
        long negCorrect = negativeFallbackCorrect.stream().filter(Boolean::booleanValue).count();
        fb.put("negativeCorrectRate", negativeFallbackCorrect.isEmpty() ? 0.0
                : round4((double) negCorrect / negativeFallbackCorrect.size()));
        s.put("fallback", fb);

        Map<String, Object> rr = new LinkedHashMap<>();
        rr.put("officialQueries", rerankOfficialQueries);
        rr.put("meanRankShift", round4(BaselineEvaluator.mean(rerankShifts)));
        long improved = rerankShifts.stream().filter(v -> v > 0).count();
        rr.put("improvedRatio", rerankShifts.isEmpty() ? 0.0
                : round4((double) improved / rerankShifts.size()));
        s.put("rerank", rr);

        return s;
    }

    private static Map<String, Object> irSummary(List<List<String>> retrieved, List<Set<String>> relevant, int topK) {
        List<Double> hit5 = new ArrayList<>(), hit10 = new ArrayList<>();
        List<Double> rec5 = new ArrayList<>(), rec10 = new ArrayList<>();
        List<Double> prec5 = new ArrayList<>(), prec10 = new ArrayList<>();
        List<Double> mrr = new ArrayList<>();
        List<Double> ndcg5 = new ArrayList<>(), ndcg10 = new ArrayList<>();
        for (int i = 0; i < retrieved.size(); i++) {
            List<String> r = retrieved.get(i);
            Set<String> rel = relevant.get(i);
            hit5.add(BaselineEvaluator.hitAtK(r, rel, 5));
            hit10.add(BaselineEvaluator.hitAtK(r, rel, 10));
            rec5.add(BaselineEvaluator.recallAtK(r, rel, 5));
            rec10.add(BaselineEvaluator.recallAtK(r, rel, 10));
            prec5.add(BaselineEvaluator.precisionAtK(r, rel, 5));
            prec10.add(BaselineEvaluator.precisionAtK(r, rel, 10));
            mrr.add(BaselineEvaluator.reciprocalRankAtK(r, rel, topK));
            ndcg5.add(BaselineEvaluator.ndcgAtK(r, rel, 5));
            ndcg10.add(BaselineEvaluator.ndcgAtK(r, rel, 10));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", retrieved.size());
        m.put("hit@5", round4(BaselineEvaluator.mean(hit5)));
        m.put("hit@10", round4(BaselineEvaluator.mean(hit10)));
        m.put("recall@5", round4(BaselineEvaluator.mean(rec5)));
        m.put("recall@10", round4(BaselineEvaluator.mean(rec10)));
        m.put("precision@5", round4(BaselineEvaluator.mean(prec5)));
        m.put("precision@10", round4(BaselineEvaluator.mean(prec10)));
        m.put("mrr@" + topK, round4(BaselineEvaluator.mean(mrr)));
        m.put("ndcg@5", round4(BaselineEvaluator.mean(ndcg5)));
        m.put("ndcg@10", round4(BaselineEvaluator.mean(ndcg10)));
        return m;
    }

    @SuppressWarnings("unchecked")
    private String renderReport(Map<String, Object> root) {
        Map<String, Object> config = (Map<String, Object>) root.get("config");
        Map<String, Object> corpus = (Map<String, Object>) root.get("corpus");
        Map<String, Object> summary = (Map<String, Object>) root.get("summary");
        Map<String, Object> synOpen = (Map<String, Object>) summary.get("synthetic_open");
        Map<String, Object> synLocked = (Map<String, Object>) summary.get("synthetic_version_locked");
        Map<String, Object> curated = (Map<String, Object>) summary.get("curated");
        Map<String, Object> fallback = (Map<String, Object>) summary.get("fallback");
        Map<String, Object> rerank = (Map<String, Object>) summary.get("rerank");
        int topK = ((Number) config.get("topK")).intValue();

        StringBuilder b = new StringBuilder();
        b.append("# 检索基线测评报告\n\n");
        b.append("> 生成时间：").append(root.get("generatedAt")).append("\n\n");
        b.append("## 1. 测评口径\n\n");
        b.append("| 项 | 值 |\n|---|---|\n");
        b.append("| 抽样规模（合成自检索） | ").append(config.get("sample")).append(" |\n");
        b.append("| TopK | ").append(topK).append("（IR 同时统计 5/10 两档） |\n");
        b.append("| 兜底阈值 app.rag.similarity-threshold | ").append(config.get("similarityThreshold")).append(" |\n");
        b.append("| 权威 boost app.rag.boost | ").append(config.get("boost")).append(" |\n");
        b.append("| 抽样随机种子 | ").append(config.get("seed")).append(" |\n");
        b.append("| 活跃文档数 | ").append(corpus.get("activeDocs")).append(" |\n");
        b.append("| 实际抽样文档数 | ").append(corpus.get("sampledDocs")).append(" |\n");
        b.append("| 精选问句数 / 未解析跳过 | ").append(corpus.get("curatedQueries"))
                .append(" / ").append(corpus.get("curatedSkipped")).append(" |\n");
        b.append("| Embedding | 真实百炼 text-embedding-v4（1024 维，不 mock） |\n\n");

        b.append("## 2. 指标摘要\n\n");
        b.append("### 2.1 标准检索 IR（合成自检索 · 开放版本）\n\n");
        b.append(irTable(synOpen));
        b.append("\n### 2.2 版本过滤正确性（合成自检索 · 锁定版本）\n\n");
        b.append("| 指标 | 值 |\n|---|---|\n");
        b.append("| 查询数 | ").append(synLocked.get("count")).append(" |\n");
        b.append("| 目标命中率 targetHit | ").append(fmt(synLocked.get("targetHit"))).append(" |\n");
        b.append("| 跨版本泄漏率 leakRate | ").append(fmt(synLocked.get("leakRate"))).append("（应为 0） |\n");
        b.append("| 正确率 correctness | ").append(fmt(synLocked.get("correctness"))).append(" |\n");

        b.append("\n### 2.3 精选真实问句（贴近用户）\n\n");
        b.append(irTable(curated));

        b.append("\n### 2.4 兜底行为\n\n");
        b.append("| 指标 | 值 |\n|---|---|\n");
        b.append("| 兜底率（可答查询 topScore < 阈值） | ").append(fmt(fallback.get("rate"))).append(" |\n");
        b.append("| topScore 最小 / 中位 / 最大 | ").append(fmt(fallback.get("minTopScore")))
                .append(" / ").append(fmt(fallback.get("medianTopScore")))
                .append(" / ").append(fmt(fallback.get("maxTopScore"))).append(" |\n");
        b.append("| 负例正确兜底率 | ").append(fmt(fallback.get("negativeCorrectRate"))).append(" |\n");

        b.append("\n### 2.5 权威重排效果（能力二）\n\n");
        b.append("| 指标 | 值 |\n|---|---|\n");
        b.append("| 含 official 相关文档的查询数 | ").append(rerank.get("officialQueries")).append(" |\n");
        b.append("| 平均名次提升（初检rank - 重排rank） | ").append(fmt(rerank.get("meanRankShift"))).append(" |\n");
        b.append("| 名次提升占比 | ").append(fmt(rerank.get("improvedRatio"))).append(" |\n");

        b.append("\n## 3. 方法论与已知偏差\n\n");
        b.append("1. **合成自检索高估**：查询由文档自身标题/首块内容构造，属「按文档自己找自己」，"
                + "其结果上界高于真实用户问法；真实读值以 §2.3 精选问句为准。\n");
        b.append("2. **社区（community）标题特异性低**：Stack Overflow 问答标题为句子，"
                + "「标题自检索」难精确命中同一文档，属预期偏差，不反映检索故障。\n");
        b.append("3. **阈值/boost 来源**：读取 application.yml 实际配置，非代码默认值。\n");
        b.append("4. **相关性粒度**：合成 = doc 级单相关；精选 = 标题级（标题 → 该标题全部 doc_id 视为相关）。\n");
        b.append("5. **可复现**：抽样固定 seed=").append(config.get("seed")).append("，语料为活跃 1445 篇；"
                + "结果明细见 data/baseline/result-*.json。\n");
        return b.toString();
    }

    private static String irTable(Map<String, Object> ir) {
        return "| 指标 | 值 |\n|---|---|\n"
                + "| 查询数 | " + ir.get("count") + " |\n"
                + "| Hit@5 / Hit@10 | " + fmt(ir.get("hit@5")) + " / " + fmt(ir.get("hit@10")) + " |\n"
                + "| Recall@5 / Recall@10 | " + fmt(ir.get("recall@5")) + " / " + fmt(ir.get("recall@10")) + " |\n"
                + "| Precision@5 / Precision@10 | " + fmt(ir.get("precision@5")) + " / " + fmt(ir.get("precision@10")) + " |\n"
                + "| MRR@10 | " + fmtOrDash(ir.get("mrr@10"), ir) + " |\n"
                + "| nDCG@5 / nDCG@10 | " + fmt(ir.get("ndcg@5")) + " / " + fmt(ir.get("ndcg@10")) + " |\n";
    }

    private static String fmtOrDash(Object v, Map<String, Object> ir) {
        // topK=5 时无 mrr@10，退化取 mrr@5
        if (v == null) {
            v = ir.get("mrr@5");
        }
        return fmt(v);
    }

    private static String fmt(Object v) {
        if (v == null) {
            return "-";
        }
        return v instanceof Double d ? String.format("%.4f", d) : String.valueOf(v);
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0d) / 10000.0d;
    }

    private static double median(List<Double> sorted) {
        if (sorted.isEmpty()) {
            return 0.0;
        }
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private static int resolveInt(ApplicationArguments args, String name, int def) {
        List<String> values = args.getOptionValues(name);
        String raw = values == null || values.isEmpty() ? null : values.get(0);
        if (raw == null || raw.isBlank()) {
            return def;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return v > 0 ? v : def;
        } catch (NumberFormatException e) {
            log.warn("非法参数 {}={}，回落到默认 {}", name, raw, def);
            return def;
        }
    }

    private List<CuratedQuery> loadCurated() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("eval/queries.json")) {
            if (in == null) {
                log.warn("[baseline] resources/eval/queries.json 不存在，精选问句为空");
                return List.of();
            }
            CuratedQuery[] arr = jsonMapper.readValue(in, CuratedQuery[].class);
            return List.of(arr);
        } catch (Exception e) {
            log.error("[baseline] 解析精选问句失败", e);
            return List.of();
        }
    }

    /** 检索结果值对象：系统输出（重排后）与初检原始序、topScore。 */
    private record SearchOutcome(List<String> retrieved, List<String> retrievedVersions,
            double topScore, List<String> rawOrder, List<String> rerankedOrder) {
    }

    /** 活跃语料文档（含首块 heading 与正文，用于构造合成查询）。 */
    private record CorpusDoc(String id, String product, String title, String version,
            String authority, String headingPath, String contentText) {
    }
}