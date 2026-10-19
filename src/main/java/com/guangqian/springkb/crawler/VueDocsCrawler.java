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
 * Vue.js 官方文档（vuejs.org，VitePress 静态站）
 * 维度：语言（en/zh）+ 文档类型（guide/api/tutorial）
 */
@Component
public class VueDocsCrawler implements DocumentCrawler {

    private static final Logger log = LoggerFactory.getLogger(VueDocsCrawler.class);
    private static final String HOME = "https://vuejs.org";
    private static final String SITEMAP = HOME + "/sitemap.xml";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SpringKB-Crawler/0.1";

    @Override
    public String source() {
        return "vue";
    }

    @Override
    public List<RawDocument> crawlSample(int limit) {
        List<String[]> urlEntries = collectUrls(); // [url, lastmod]
        log.info("[vue] sitemap 共 {} 个 URL", urlEntries.size());
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
                log.warn("[vue] 抓取失败 {} : {}", entry[0], e.getMessage());
            }
        }
        log.info("[vue] 样本文档 {} 篇", result.size());
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
            log.warn("[vue] sitemap 解析失败 {} : {}", url, e.getMessage());
        }
    }

    private boolean isDocPage(String url) {
        if (!url.startsWith(HOME) || url.contains("/404")) {
            return false;
        }
        return GUIDE.matcher(url).find()
                || API.matcher(url).find()
                || TUTORIAL.matcher(url).find();
    }

    private static final java.util.regex.Pattern GUIDE = java.util.regex.Pattern.compile("/guide/");
    private static final java.util.regex.Pattern API = java.util.regex.Pattern.compile("/api/");
    private static final java.util.regex.Pattern TUTORIAL = java.util.regex.Pattern.compile("/tutorial/");

    private RawDocument fetch(String url, String lastmod) throws Exception {
        Document doc = Jsoup.connect(url).userAgent(UA).timeout(20000).get();
        Element root = doc.selectFirst(".vp-doc, .theme-default-content, main");
        if (root == null) {
            return null;
        }
        HtmlToMarkdown.Result md = HtmlToMarkdown.convert(root, url);
        if (md.markdown().isEmpty()) {
            return null;
        }
        String title = HtmlText.normalize(doc.title()).replace(" | Vue.js", "");
        if (title.isEmpty()) {
            title = "untitled";
        }
        String path = url.substring(HOME.length());
        String lang = path.startsWith("/zh/") ? "zh" : "en";
        String key = path.replaceFirst("^/" + lang + "/", "/").replaceFirst("/index(\\.html)?$", "");
        String docType = docTypeOf(key);
        if (docType == null) {
            return null; // 非 guide/api/tutorial 页面跳过
        }
        String section = sectionOf(key);
        String text = HtmlText.normalize(root.text()); // 纯文本（去导航噪声）
        return new RawDocument("vue", "vue", "3.x", lang, docType, title, url, key,
                section, List.of(), lastmod, text, md.markdown(), md.images(), md.headings());
    }

    private static String docTypeOf(String key) {
        if (key.startsWith("/guide/")) return "guide";
        if (key.startsWith("/api/")) return "api";
        if (key.startsWith("/tutorial/")) return "tutorial";
        return null;
    }

    private static String sectionOf(String key) {
        String[] parts = key.split("/");
        if (parts.length >= 3) {
            return parts[2]; // 如 guide/essentials 下的第二段
        }
        return parts.length >= 2 ? parts[1] : key;
    }
}
