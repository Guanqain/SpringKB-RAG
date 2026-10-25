package com.guangqian.springkb.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * 从 SecurityContext 提取当前登录用户名：匿名/未认证返回 null。
 * 公开接口（/api/kb/**）也会拿到 AnonymousAuthenticationToken，必须显式排除。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static String username(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        String name = auth.getName();
        return name == null || name.isBlank() || "anonymousUser".equals(name) ? null : name;
    }

    /** 需要登录的接口：在 SecurityConfig 已保证认证的前提下取用户名，极端情况下兜底抛 401 语义异常。 */
    public static String require(Authentication auth) {
        String username = username(auth);
        if (username == null) {
            throw new IllegalArgumentException("未登录");
        }
        return username;
    }
}
