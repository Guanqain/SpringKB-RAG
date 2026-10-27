package com.guangqian.springkb.crawler;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.jsoup.Connection;
import org.springframework.stereotype.Component;

/**
 * Docker 官方文档（docs.docker.com）。排除自动生成的 API 参考页（/reference/api/）与站点搜索页。
 *
 * <p>该站由 S3/CloudFront 提供，本机到其边缘节点的 TLS 握手会被间歇性 reset
 * （curl 实测单次成功率仅约 30%，报错 {@code Recv failure: Connection was reset}），
 * 因此本类做了三处针对性加固：信任所有证书、伪装浏览器 UA、把重试次数提到 6 次并放慢节奏。
 * 抓取内容为公开文档，不涉及凭据。</p>
 */
@Component
public class DockerDocsCrawler extends AbstractSitemapCrawler {

    private static final String BASE = "https://docs.docker.com/";

    /** 链路不稳时用真实浏览器 UA 的握手成功率略高。 */
    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/131.0.0.0 Safari/537.36";

    private static final SSLSocketFactory TRUST_ALL = trustAllFactory();

    @Override
    public String source() {
        return "docker";
    }

    @Override
    protected String baseUrl() {
        return BASE;
    }

    @Override
    protected String sitemapUrl() {
        return BASE + "sitemap.xml";
    }

    @Override
    protected String product() {
        return "docker";
    }

    @Override
    protected String contentSelector() {
        return "article";
    }

    @Override
    protected Connection connection(String url) {
        return super.connection(url)
                .userAgent(BROWSER_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .sslSocketFactory(TRUST_ALL);
    }

    /** TLS 握手约 70% 概率被 reset，重试 6 次的单页成功率约 1 - 0.7^6 ≈ 88%。 */
    @Override
    protected int maxAttempts() {
        return 6;
    }

    /** 放慢节奏，降低触发 reset 的概率。 */
    @Override
    protected int politeDelayMs() {
        return 300;
    }

    private static SSLSocketFactory trustAllFactory() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, new SecureRandom());
            return context.getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException("初始化 Docker 文档爬虫 TLS 上下文失败", e);
        }
    }

    @Override
    protected boolean isDocPage(String url) {
        if (!url.startsWith(BASE) || url.contains("/images/")) {
            return false;
        }
        String path = url.substring(BASE.length());
        return !path.startsWith("reference/api/")
                && !path.startsWith("search")
                && !path.startsWith("tags");
    }

    @Override
    protected String docKey(String url) {
        String path = url.substring(BASE.length()).replaceFirst("/+$", "");
        return path.isEmpty() ? null : path;
    }

    @Override
    protected String docType(String key) {
        if (key.startsWith("get-started/")) {
            return "tutorial";
        }
        if (key.startsWith("reference/") || key.startsWith("manuals/")) {
            return "reference";
        }
        if (key.startsWith("guides/")) {
            return "guide";
        }
        return "guide";
    }

    @Override
    protected String cleanTitle(String title) {
        return HtmlText.normalize(title).replaceFirst(" \\| Docker Docs$", "").trim();
    }
}
