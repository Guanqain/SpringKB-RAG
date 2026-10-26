package com.guangqian.springkb.dto;

/**
 * 注册请求（POST /api/auth/register）：邮箱必须通过验证码校验后才能注册。
 */
public record RegisterRequest(String username, String email, String password, String code) {
}
