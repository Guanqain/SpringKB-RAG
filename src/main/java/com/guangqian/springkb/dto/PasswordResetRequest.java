package com.guangqian.springkb.dto;

/**
 * 忘记密码：邮箱验证码重置密码请求体（newPassword 至少 6 位）。
 */
public record PasswordResetRequest(String email, String code, String newPassword) {
}
