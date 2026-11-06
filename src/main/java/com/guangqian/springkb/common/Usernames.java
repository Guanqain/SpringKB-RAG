package com.guangqian.springkb.common;

import java.util.regex.Pattern;

/**
 * 用户名统一规范：trim + 仅允许字母、数字、下划线、连字符，长度 3-32。
 *
 * <p>前端校验只用于提示，后端才是安全边界；注册与初始化向导都必须经过这里。</p>
 */
public final class Usernames {

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9_-]{3,32}$");

    private Usernames() {
    }

    public static String normalize(String raw) {
        String normalized = raw == null ? "" : raw.trim();
        if (!PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("用户名只能包含字母、数字、下划线和连字符，长度 3-32 位");
        }
        return normalized;
    }
}
