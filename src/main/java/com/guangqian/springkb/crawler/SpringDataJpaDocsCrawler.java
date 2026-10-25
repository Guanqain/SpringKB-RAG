package com.guangqian.springkb.crawler;

import org.springframework.stereotype.Component;

/**
 * Spring Data JPA 官方参考文档（docs.spring.io/spring-data/jpa/reference）。
 * sitemap 为 sitemapindex（data-commons + data-jpa 两个子 sitemap），由基类递归展开。
 */
@Component
public class SpringDataJpaDocsCrawler extends AbstractSpringReferenceCrawler {

    private static final String BASE = "https://docs.spring.io/spring-data/jpa/reference/";

    @Override
    public String source() {
        return "springdatajpa";
    }

    @Override
    protected String baseUrl() {
        return BASE;
    }

    @Override
    protected String product() {
        return "spring-data-jpa";
    }
}
