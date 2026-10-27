package com.guangqian.springkb.crawler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stack Overflow 社区问答（StackExchange API v2.3，不需要抓页面）。
 *
 * <p>按标签取高票问题，再按问题 id 批量取高票回答（最多 2 条，票数 &gt; 0），
 * 拼成「问题 + 回答」的 Markdown 文档，authority 由 IngestionService 映射为 community。</p>
 *
 * <p>配额：无 key 时 300 次/天，本实现约 6 次问题请求 + 若干次回答请求，
 * 请求间隔 120ms，并处理 API 返回的 backoff 字段。</p>
 */
@Component
public class StackOverflowCrawler implements DocumentCrawler {

    private static final Logger log = LoggerFactory.getLogger(StackOverflowCrawler.class);

    private static final String API = "https://api.stackexchange.com/2.3";

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SpringKB-Crawler/0.2";

    /** 抓取标签：Spring 主线 + Vue 生态。 */
    private static final List<String> TAGS = List.of(
            "spring-boot", "spring-ai", "spring-security", "spring-data-jpa", "spring", "vue.js");

    /** 标签 → 产品线（与官方文档 product 对齐，便于版本/产品过滤）。 */
    private static final Map<String, String> TAG_PRODUCT = Map.of(
            "spring-boot", "spring-boot",
            "spring-ai", "spring-ai",
            "spring-security", "spring-security",
            "spring-data-jpa", "spring-data-jpa",
            "spring", "spring-framework",
            "vue.js", "vue");

    /** 每次回答请求最多带多少个问题 id（API 上限 100）。 */
    private static final int ANSWER_CHUNK = 30;

    /** 每个问题保留的高票回答数。 */
    private static final int MAX_ANSWERS_PER_QUESTION = 2;

    private final JsonMapper jsonMapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public StackOverflowCrawler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String source() {
        return "stackoverflow";
    }

    @Override
    public List<RawDocument> crawlSample(int limit) {
        int perTag = Math.max(10, (int) Math.ceil(limit / (double) TAGS.size()));
        Map<Long, Map<String, Object>> questions = new LinkedHashMap<>();
        for (String tag : TAGS) {
            if (questions.size() >= limit) {
                break;
            }
            try {
                List<Map<String, Object>> items = fetchQuestions(tag, Math.min(100, perTag));
                for (Map<String, Object> q : items) {
                    long id = longOf(q.get("question_id"));
                    Map<String, Object> copy = new HashMap<>(q);
                    copy.put("_tag", tag);
                    questions.putIfAbsent(id, copy);
                }
                log.info("[stackoverflow] tag={} 返回 {} 条，累计候选 {} 条", tag, items.size(), questions.size());
            } catch (Exception e) {
                log.warn("[stackoverflow] 抓取问题失败 tag={} : {}", tag, e.getMessage());
            }
            sleep(120);
        }

        List<Long> ids = questions.keySet().stream().limit(limit).toList();
        Map<Long, List<Map<String, Object>>> answers = fetchAnswers(ids);
        Set<String> doneUrls = CrawlCheckpoint.doneUrls(source());
        List<RawDocument> docs = new ArrayList<>();
        for (Long id : ids) {
            Map<String, Object> question = questions.get(id);
            if (doneUrls.contains(stringOf(question.get("link")))) {
                continue; // 断点续抓：已入库的问题直接跳过
            }
            List<Map<String, Object>> top = answers.get(id);
            if (top == null || top.isEmpty()) {
                continue; // 无回答的问题对问答库价值低，跳过
            }
            try {
                RawDocument doc = toDocument(question, top);
                docs.add(doc);
                CrawlCheckpoint.append(source(), doc);
            } catch (Exception e) {
                log.warn("[stackoverflow] 组装文档失败 questionId={} : {}", id, e.getMessage());
            }
        }
        log.info("[stackoverflow] 抓取完成 {} 篇（候选问题 {} 条）", docs.size(), ids.size());
        return docs;
    }

    // ---------- API ----------

    private List<Map<String, Object>> fetchQuestions(String tag, int pageSize) throws Exception {
        String url = API + "/questions?site=stackoverflow&sort=votes&order=desc"
                + "&tagged=" + tag + "&pagesize=" + pageSize + "&filter=withbody";
        return items(url);
    }

    private Map<Long, List<Map<String, Object>>> fetchAnswers(List<Long> ids) {
        Map<Long, List<Map<String, Object>>> byQuestion = new HashMap<>();
        for (int i = 0; i < ids.size(); i += ANSWER_CHUNK) {
            List<Long> chunk = ids.subList(i, Math.min(ids.size(), i + ANSWER_CHUNK));
            String idList = chunk.stream().map(String::valueOf).collect(Collectors.joining(";"));
            String url = API + "/questions/" + idList + "/answers?site=stackoverflow"
                    + "&sort=votes&order=desc&pagesize=100&filter=withbody";
            try {
                for (Map<String, Object> a : items(url)) {
                    long qid = longOf(a.get("question_id"));
                    List<Map<String, Object>> list = byQuestion.computeIfAbsent(qid, k -> new ArrayList<>());
                    if (list.size() >= MAX_ANSWERS_PER_QUESTION) {
                        continue;
                    }
                    boolean accepted = Boolean.TRUE.equals(a.get("is_accepted"));
                    if (accepted || longOf(a.get("score")) > 0) {
                        list.add(a);
                    }
                }
            } catch (Exception e) {
                log.warn("[stackoverflow] 抓取回答失败 ids={}.. : {}", chunk.get(0), e.getMessage());
            }
            sleep(120);
        }
        return byQuestion;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(String url) throws Exception {
        Map<String, Object> body = getJson(url);
        handleBackoff(body);
        Object quota = body.get("quota_remaining");
        if (quota != null) {
            log.debug("[stackoverflow] 剩余配额 {}", quota);
        }
        Object raw = body.get("items");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(o -> (Map<String, Object>) o).toList();
    }

    private Map<String, Object> getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                // 关闭 gzip，避免手动解压；API 在 identity 下返回明文 JSON
                .header("Accept-Encoding", "identity")
                .header("User-Agent", UA)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " " + url);
        }
        return jsonMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {
        });
    }

    private static void handleBackoff(Map<String, Object> body) {
        Object backoff = body.get("backoff");
        if (backoff != null) {
            long seconds = Math.min(10, longOf(backoff));
            log.warn("[stackoverflow] API 要求退避 {} 秒", seconds);
            sleep(seconds * 1000);
        }
    }

    // ---------- 文档组装 ----------

    RawDocument toDocument(Map<String, Object> q, List<Map<String, Object>> answers) {
        long id = longOf(q.get("question_id"));
        String title = stringOf(q.get("title"));
        String link = stringOf(q.get("link"));
        String tag = stringOf(q.get("_tag"));
        String questionBody = stringOf(q.get("body"));

        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(title).append("\n\n");
        markdown.append("> 来源：Stack Overflow · 问题票数 ").append(longOf(q.get("score")))
                .append(" · 标签 ").append(joinTags(q.get("tags"))).append("\n\n");
        markdown.append("## 问题\n\n").append(htmlToMarkdown(questionBody, link));

        StringBuilder text = new StringBuilder(title).append('\n').append(htmlToText(questionBody));
        for (Map<String, Object> a : answers) {
            boolean accepted = Boolean.TRUE.equals(a.get("is_accepted"));
            markdown.append("\n\n## ").append(accepted ? "已采纳回答" : "回答")
                    .append("（票数 ").append(longOf(a.get("score"))).append("）\n\n")
                    .append(htmlToMarkdown(stringOf(a.get("body")), link));
            text.append('\n').append(htmlToText(stringOf(a.get("body"))));
        }

        String lastModified = q.get("last_activity_date") == null ? null
                : Instant.ofEpochSecond(longOf(q.get("last_activity_date"))).toString();
        String product = TAG_PRODUCT.getOrDefault(tag, "spring-framework");

        return new RawDocument("stackoverflow", product, "latest", "en", "qa", title, link,
                "/questions/" + id, tag, List.of(), lastModified,
                text.toString(), markdown.toString(), List.of(), List.of());
    }

    private static String htmlToMarkdown(String html, String baseUrl) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return HtmlToMarkdown.convert(Jsoup.parseBodyFragment(html, baseUrl).body(), baseUrl).markdown();
    }

    private static String htmlToText(String html) {
        return html == null || html.isBlank() ? "" : Jsoup.parse(html).text();
    }

    private static String joinTags(Object tags) {
        if (tags instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
        return "";
    }

    private static String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static long longOf(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
