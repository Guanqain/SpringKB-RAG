package com.guangqian.springkb.dto;

/**
 * 发送邮箱验证码请求（POST /api/auth/email-code）。
 * purpose：register（注册）/ login（验证码登录）/ bind（绑定邮箱）。
 */
public record EmailCodeRequest(String email, String purpose) {
}
