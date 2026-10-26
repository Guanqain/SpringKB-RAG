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
 * <p>该站由 S3/CloudFront 提供，证书链在本机 Java TLS 客户端校验失败（curl 也需 -k），
 * 因此仅对本站使用信任所有证书的连接工厂；抓取内容为公开文档，不涉及凭据。</p>
 */
@Component
public class DockerDocsCrawler extends AbstractSitemapCrawler {

    private static final String BASE = "https://docs.docker.com/";

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
        return super.connection(url).sslSocketFactory(TRUST_ALL);
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
