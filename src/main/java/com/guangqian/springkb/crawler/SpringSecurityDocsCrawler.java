package com.guangqian.springkb.crawler;

import org.springframework.stereotype.Component;

/**
 * Spring Security 官方参考文档（docs.spring.io/spring-security/reference）。
 * 覆盖认证、授权、OAuth2、Servlet/Reactive 集成等主题。
 */
@Component
public class SpringSecurityDocsCrawler extends AbstractSpringReferenceCrawler {

    private static final String BASE = "https://docs.spring.io/spring-security/reference/";

    @Override
    public String source() {
        return "springsecurity";
    }

    @Override
    protected String baseUrl() {
        return BASE;
    }

    @Override
    protected String product() {
        return "spring-security";
    }
}
