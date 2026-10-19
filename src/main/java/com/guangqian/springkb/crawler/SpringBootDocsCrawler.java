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
 * Spring Boot 官方文档（docs.spring.io/spring-boot，Antora 静态站）
 * 维度：Spring 全家桶代表；先抓 reference 主线（latest），多版本后续扩展
 */
@Component
public class SpringBootDocsCrawler implements DocumentCrawler {

    private static final Logger log = LoggerFactory.getLogger(SpringBootDocsCrawler.class);
    private static final String BASE = "https://docs.spring.io/spring-boot/";
    /** spring-boot 的 sitemap 是项目级（urlset，13MB+，含全版本 javadoc 与 reference） */
    private static final String SITEMAP = "https://docs.spring.io/spring-boot/sitemap.xml";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SpringKB-Crawler/0.1";

    @Override
    public String source() {
        return "springboot";
    }

    @Override
    public List<RawDocument> crawlSample(int limit) {
        List<String[]> urlEntries = collectUrls();
        log.info("[springboot] sitemap 共 {} 个 URL", urlEntries.size());
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
                log.warn("[springboot] 抓取失败 {} : {}", entry[0], e.getMessage());
            }
        }
        log.info("[springboot] 样本文档 {} 篇", result.size());
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
            log.warn("[springboot] sitemap 解析失败 {} : {}", url, e.getMessage());
        }
    }

    private boolean isDocPage(String url) {
        // 只要 reference 文档页（指南），排除 javadoc /api 与 index/images 等
        return url.startsWith(BASE)
                && url.contains("/reference/")
                && url.endsWith(".html")
                && !url.contains("index.html")
                && !url.contains("images/");
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
        String path = url.substring(BASE.length()); // 形如 4.2-SNAPSHOT/reference/actuator/enabling.html
        String version = path.split("/")[0];        // 真实版本号：4.2-SNAPSHOT
        String key = path.replaceFirst("^[^/]+/reference/", "").replaceFirst("\\.html$", "");
        if (key.isEmpty()) {
            key = "index";
        }
        String docType = docTypeOf(key);
        String text = HtmlText.normalize(root.text()); // 纯文本（去导航噪声）
        return new RawDocument("springboot", "spring-boot", version, "en", docType, title, url, key,
                null, List.of(), lastmod, text, md.markdown(), md.images(), md.headings());
    }

    private static String docTypeOf(String key) {
        if (key.startsWith("how-to/")) return "howto";
        if (key.startsWith("api/")) return "api";
        return "reference";
    }
}
