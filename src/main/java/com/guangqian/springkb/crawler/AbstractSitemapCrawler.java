package com.guangqian.springkb.crawler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 sitemap 的文档站爬虫基类：统一处理 sitemap 递归解析、页面过滤、抓取、限速、重试与进度日志。
 *
 * <p>子类只需声明站点信息（baseUrl / sitemapUrl / product）与 URL → docKey 映射规则，
 * 避免每个数据源重复实现同一套抓取逻辑。</p>
 */
public abstract class AbstractSitemapCrawler implements DocumentCrawler {

    private static final Logger log = LoggerFactory.getLogger(AbstractSitemapCrawler.class);

    protected static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SpringKB-Crawler/0.2";

    /** 单次请求超时（毫秒）。 */
    private static final int FETCH_TIMEOUT_MS = 25_000;

    /** 请求间隔：礼貌抓取，避免对文档站造成压力。 */
    private static final int POLITE_DELAY_MS = 120;

    /** 单页最多尝试次数（网络抖动重试）。 */
    private static final int MAX_ATTEMPTS = 3;

    /** 进度日志间隔。 */
    private static final int PROGRESS_EVERY = 50;

    /** 站点根地址（以 / 结尾），用于截取 docKey。 */
    protected abstract String baseUrl();

    /** sitemap 地址（可为 sitemapindex，基类会递归展开）。 */
    protected abstract String sitemapUrl();

    /** 产品线标识，如 spring-framework / vite。 */
    protected abstract String product();

    /** URL → docKey；返回 null 表示跳过该页。 */
    protected abstract String docKey(String url);

    /** 正文根节点选择器。 */
    protected String contentSelector() {
        return "article.doc, div.doc, .content, main";
    }

    /** 页面是否属于文档页（默认只要求同域且不是图片资源）。 */
    protected boolean isDocPage(String url) {
        return url.startsWith(baseUrl()) && !url.contains("/images/");
    }

    /** 文档版本；URL 中不含版本信息时用 latest。 */
    protected String version(String url) {
        return "latest";
    }

    /** 文档类型：guide / api / tutorial / reference / howto / qa。 */
    protected String docType(String key) {
        return "reference";
    }

    /** 章节（可为 null）。 */
    protected String section(String key) {
        return null;
    }

    /** 文档语言。 */
    protected String language(String url) {
        return "en";
    }

    /** 标题清理（去掉站点后缀等）。 */
    protected String cleanTitle(String title) {
        return HtmlText.normalize(title);
    }

    /** 连接配置钩子：个别站点（如 docs.docker.com）需要自定义 TLS 处理。 */
    protected Connection connection(String url) {
        return Jsoup.connect(url).userAgent(UA).timeout(FETCH_TIMEOUT_MS);
    }

    @Override
    public List<RawDocument> crawlSample(int limit) {
        boolean unlimited = limit <= 0;
        List<String[]> entries = collectUrls();
        log.info("[{}] sitemap 共 {} 个候选 URL，篇数上限={}", source(), entries.size(),
                unlimited ? "不限" : String.valueOf(limit));
        List<RawDocument> result = new ArrayList<>();
        for (String[] entry : entries) {
            if (!unlimited && result.size() >= limit) {
                break;
            }
            try {
                RawDocument doc = fetch(entry[0], entry[1]);
                if (doc != null) {
                    result.add(doc);
                    if (result.size() % PROGRESS_EVERY == 0) {
                        log.info("[{}] 已抓取 {} 篇", source(), result.size());
                    }
                }
            } catch (Exception e) {
                log.warn("[{}] 抓取失败 {} : {}", source(), entry[0], e.getMessage());
            }
            sleep(POLITE_DELAY_MS);
        }
        log.info("[{}] 抓取完成 {} 篇", source(), result.size());
        return result;
    }

    /** 递归展开 sitemap/index，去重后仅保留文档页。 */
    protected List<String[]> collectUrls() {
        List<String[]> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        parseSitemap(sitemapUrl(), out, seen, 0);
        return out;
    }

    private void parseSitemap(String url, List<String[]> out, Set<String> seen, int depth) {
        if (depth > 2) {
            log.warn("[{}] sitemap 嵌套过深，跳过 {}", source(), url);
            return;
        }
        try {
            Document doc = fetchWithRetry(url);
            if (doc.selectFirst("sitemapindex") != null) {
                for (Element loc : doc.select("sitemapindex > sitemap > loc")) {
                    parseSitemap(loc.text().trim(), out, seen, depth + 1);
                }
                return;
            }
            for (Element urlEl : doc.select("url")) {
                Element locEl = urlEl.selectFirst("loc");
                if (locEl == null) {
                    continue;
                }
                String loc = locEl.text().trim();
                if (!isDocPage(loc) || !seen.add(loc)) {
                    continue;
                }
                Element lastmodEl = urlEl.selectFirst("lastmod");
                out.add(new String[]{loc, lastmodEl == null ? "" : lastmodEl.text().trim()});
            }
        } catch (Exception e) {
            log.warn("[{}] sitemap 解析失败 {} : {}", source(), url, e.getMessage());
        }
    }

    private RawDocument fetch(String url, String lastmod) throws Exception {
        Document doc = fetchWithRetry(url);
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
                section(key), List.of(), lastmod, text, md.markdown(), md.images(), md.headings());
    }

    private Document fetchWithRetry(String url) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return connection(url).get();
            } catch (Exception e) {
                last = e;
                sleep(400L * attempt);
            }
        }
        throw last;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
