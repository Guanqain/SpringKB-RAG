package com.guangqian.springkb.dto;

/**
 * 绑定邮箱请求（POST /api/me/email，需登录，验证码 purpose=bind）。
 */
public record BindEmailRequest(String email, String code) {
}
