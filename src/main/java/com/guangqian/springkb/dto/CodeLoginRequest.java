package com.guangqian.springkb.dto;

/**
 * 验证码登录请求（POST /api/auth/login/code）。
 */
public record CodeLoginRequest(String email, String code) {
}
