package com.guangqian.springkb.crawler;

import org.springframework.stereotype.Component;

/**
 * Spring Framework 官方参考文档（docs.spring.io/spring-framework/reference）。
 * 覆盖 IoC、AOP、数据访问、Web、Reactive、测试等核心主题。
 */
@Component
public class SpringFrameworkDocsCrawler extends AbstractSpringReferenceCrawler {

    private static final String BASE = "https://docs.spring.io/spring-framework/reference/";

    @Override
    public String source() {
        return "springframework";
    }

    @Override
    protected String baseUrl() {
        return BASE;
    }

    @Override
    protected String product() {
        return "spring-framework";
    }
}
