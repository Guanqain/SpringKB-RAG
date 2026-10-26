package com.guangqian.springkb.dto;

/**
 * 忘记密码：发送重置验证码请求体（purpose 固定 reset，不允许客户端指定）。
 */
public record PasswordCodeRequest(String email) {
}
