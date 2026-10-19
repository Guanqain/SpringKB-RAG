package com.guangqian.springkb.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI 官方文档（docs.spring.io/spring-ai，Antora 静态站）
 * 维度：AI/agent 框架文档，正文为 Spring 文档统一结构
 */
@Component
public class SpringAiDocsCrawler implements DocumentCrawler {

    private static final Logger log = LoggerFactory.getLogger(SpringAiDocsCrawler.class);
    private static final String BASE = "https://docs.spring.io/spring-ai/reference";
    private static final String SITEMAP = BASE + "/sitemap.xml";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SpringKB-Crawler/0.1";

    @Override
    public String source() {
        return "springai";
    }

    @Override
    public List<RawDocument> crawlSample(int limit) {
        List<String[]> urlEntries = collectUrls();
        log.info("[springai] sitemap 共 {} 个 URL", urlEntries.size());
        List<RawDocument> result = new ArrayList<>();
        for (String[] entry : urlEntries) {
            if (result.size() >= limit) {
                break;
            }
            try {
                RawDocument doc = fetch(entry[0], entry[1]);
                if (doc != null) {
                    result.add(doc);
                }
            } catch (Exception e) {
                log.warn("[springai] 抓取失败 {} : {}", entry[0], e.getMessage());
            }
        }
        log.info("[springai] 样本文档 {} 篇", result.size());
        return result;
    }

    private List<String[]> collectUrls() {
        List<String[]> out = new ArrayList<>();
        parseSitemap(SITEMAP, out);
        return out;
    }

    private void parseSitemap(String url, List<String[]> out) {
        try {
            Document doc = Jsoup.connect(url).userAgent(UA).timeout(20000).get();
            if (doc.selectFirst("sitemapindex") != null) {
                for (Element loc : doc.select("sitemapindex > sitemap > loc")) {
                    parseSitemap(loc.text().trim(), out);
                }
            } else {
                for (Element urlEl : doc.select("url")) {
                    String loc = urlEl.selectFirst("loc") == null ? null : urlEl.selectFirst("loc").text().trim();
                    String lastmod = urlEl.selectFirst("lastmod") == null ? "" : urlEl.selectFirst("lastmod").text().trim();
                    if (loc == null || !isDocPage(loc)) {
                        continue;
                    }
                    out.add(new String[]{loc, lastmod});
                }
            }
        } catch (Exception e) {
            log.warn("[springai] sitemap 解析失败 {} : {}", url, e.getMessage());
        }
    }

    private boolean isDocPage(String url) {
        return url.startsWith(BASE)
                && !url.contains("images/")
                && !url.contains("css/")
                && !url.contains("js/")
                && !url.contains("index.html");
    }

    private RawDocument fetch(String url, String lastmod) throws Exception {
        Document doc = Jsoup.connect(url).userAgent(UA).timeout(20000).get();
        Element root = doc.selectFirst("article.doc, div.doc, .content, main");
        if (root == null) {
            return null;
        }
        HtmlToMarkdown.Result md = HtmlToMarkdown.convert(root, url);
        if (md.markdown().isEmpty()) {
            return null;
        }
        String title = HtmlText.normalize(doc.title());
        if (title.isEmpty()) {
            title = "untitled";
        }
        String path = url.substring(BASE.length()).replaceFirst("(\\.html)?$", "");
        String key = path.replaceAll("^/", "");
        if (key.isEmpty()) {
            key = "index";
        }
        String docType = "reference";
        if (key.startsWith("api/")) {
            docType = "api";
        }
        String text = HtmlText.normalize(root.text()); // 纯文本（去导航噪声）
        return new RawDocument("springai", "spring-ai", "latest", "en", docType, title, url, key,
                null, List.of(), lastmod, text, md.markdown(), md.images(), md.headings());
    }
}
