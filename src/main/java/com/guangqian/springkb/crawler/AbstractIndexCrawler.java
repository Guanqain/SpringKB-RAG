package com.guangqian.springkb.crawler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 无 sitemap 文档站的通用爬虫基类：从入口页开始做限定深度的 BFS，边抓正文边发现站内链接。
 *
 * <p>适用于 go.dev / docs.python.org 这类没有 sitemap、但侧边栏提供完整目录的站点。
 * 子类只需声明入口页、站点前缀过滤与 docKey 规则。</p>
 */
public abstract class AbstractIndexCrawler implements DocumentCrawler {

    private static final Logger log = LoggerFactory.getLogger(AbstractIndexCrawler.class);

    protected static final String UA = AbstractSitemapCrawler.UA;

    private static final int FETCH_TIMEOUT_MS = 25_000;

    private static final int POLITE_DELAY_MS = 120;

    private static final int MAX_ATTEMPTS = 2;

    private static final int PROGRESS_EVERY = 50;

    /** 非文档资源后缀，链接发现阶段直接跳过。 */
    private static final Set<String> SKIP_EXTENSIONS = Set.of(
            "pdf", "zip", "gz", "tgz", "tar", "png", "jpg", "jpeg", "gif", "svg", "webp",
            "css", "js", "xml", "json", "txt", "ico", "mp4", "woff", "woff2");

    /** BFS 入口页（可多个）。 */
    protected abstract List<String> entryPages();

    /** 产品线标识。 */
    protected abstract String product();

    /** 是否属于要抓取的文档页。 */
    protected abstract boolean isDocPage(String url);

    /** URL → docKey；返回 null 表示跳过该页。 */
    protected abstract String docKey(String url);

    /** BFS 最大深度（入口页为 0）。 */
    protected int maxDepth() {
        return 2;
    }

    /** 单次运行最多抓取的页面数（防失控），0 表示不限制。 */
    protected int maxPages() {
        return 6000;
    }

    /** 正文根节点选择器。 */
    protected String contentSelector() {
        return "article, main, #content, .content";
    }

    protected String version(String url) {
        return "latest";
    }

    protected String docType(String key) {
        return "reference";
    }

    protected String section(String key) {
        if (key == null) {
            return null;
        }
        int slash = key.indexOf('/');
        return slash > 0 ? key.substring(0, slash) : key;
    }

    protected String language(String url) {
        return "en";
    }

    protected String cleanTitle(String title) {
        return HtmlText.normalize(title);
    }

    @Override
    public List<RawDocument> crawlSample(int limit) {
        boolean unlimited = limit <= 0;
        List<RawDocument> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Deque<String[]> queue = new ArrayDeque<>();
        for (String entry : entryPages()) {
            String url = normalizeUrl(entry);
            if (seen.add(url)) {
                queue.add(new String[]{url, "0"});
            }
        }
        int fetched = 0;
        int cap = maxPages();
        while (!queue.isEmpty()) {
            if (!unlimited && result.size() >= limit) {
                break;
            }
            if (cap > 0 && fetched >= cap) {
                log.warn("[{}] 达到单次抓取上限 {} 页，提前结束", source(), cap);
                break;
            }
            String[] item = queue.poll();
            String url = item[0];
            int depth = Integer.parseInt(item[1]);
            fetched++;
            Document doc;
            try {
                doc = fetchWithRetry(url);
            } catch (Exception e) {
                log.warn("[{}] 抓取失败 {} : {}", source(), url, e.getMessage());
                sleep(POLITE_DELAY_MS);
                continue;
            }
            if (depth < maxDepth()) {
                discoverLinks(doc, depth + 1, seen, queue);
            }
            try {
                RawDocument raw = toRawDocument(doc, url);
                if (raw != null) {
                    result.add(raw);
                    if (result.size() % PROGRESS_EVERY == 0) {
                        log.info("[{}] 已抓取 {} 篇（待抓队列 {}）", source(), result.size(), queue.size());
                    }
                }
            } catch (Exception e) {
                log.warn("[{}] 解析失败 {} : {}", source(), url, e.getMessage());
            }
            sleep(POLITE_DELAY_MS);
        }
        log.info("[{}] 抓取完成 {} 篇（累计访问 {} 页）", source(), result.size(), fetched);
        return result;
    }

    private void discoverLinks(Document doc, int nextDepth, Set<String> seen, Deque<String[]> queue) {
        for (Element anchor : doc.select("a[href]")) {
            String abs = anchor.absUrl("href");
            if (abs == null || abs.isBlank()) {
                continue;
            }
            String url = normalizeUrl(abs);
            if (!isDocPage(url) || hasSkipExtension(url) || !seen.add(url)) {
                continue;
            }
            queue.add(new String[]{url, String.valueOf(nextDepth)});
        }
    }

    private RawDocument toRawDocument(Document doc, String url) {
        Element root = doc.selectFirst(contentSelector());
        if (root == null) {
            return null;
        }
        HtmlToMarkdown.Result md = HtmlToMarkdown.convert(root, url);
        if (md.markdown().isEmpty()) {
            return null;
        }
        String key = docKey(url);
        if (key == null) {
            return null;
        }
        String title = cleanTitle(doc.title());
        if (title.isEmpty()) {
            title = "untitled";
        }
        String text = HtmlText.normalize(root.text());
        return new RawDocument(source(), product(), version(url), language(url), docType(key), title, url, key,
                section(key), List.of(), "", text, md.markdown(), md.images(), md.headings());
    }

    private Document fetchWithRetry(String url) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return Jsoup.connect(url).userAgent(UA).timeout(FETCH_TIMEOUT_MS).get();
            } catch (Exception e) {
                last = e;
                sleep(400L * attempt);
            }
        }
        throw last;
    }

    /** 去 fragment / query；相对链接已由 Jsoup 转为绝对地址。 */
    protected static String normalizeUrl(String url) {
        String normalized = url;
        int hash = normalized.indexOf('#');
        if (hash >= 0) {
            normalized = normalized.substring(0, hash);
        }
        int query = normalized.indexOf('?');
        if (query >= 0) {
            normalized = normalized.substring(0, query);
        }
        return normalized;
    }

    private static boolean hasSkipExtension(String url) {
        String path = url;
        int slash = path.lastIndexOf('/');
        if (slash >= 0) {
            path = path.substring(slash + 1);
        }
        int dot = path.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return SKIP_EXTENSIONS.contains(path.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
