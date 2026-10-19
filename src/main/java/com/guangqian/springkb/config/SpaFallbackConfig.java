package com.guangqian.springkb.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 前端 SPA history 路由回退（容器一体化部署模式）：
 * 直接刷新 /login、/documents、/documents/import 等页面路由时，
 * Spring MVC 无对应静态资源，回退转发到 index.html 由前端路由接管。
 * 仅匹配前端页面路径，不影响 /api/** 接口。
 */
@Configuration
public class SpaFallbackConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/login").setViewName("forward:/index.html");
        registry.addViewController("/documents/**").setViewName("forward:/index.html");
    }
}
