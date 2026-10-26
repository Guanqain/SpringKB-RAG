package com.guangqian.springkb.dto;

/**
 * 验证码发送结果。devCode 仅在 app.auth.code-echo=true（本地/测试）时回显，
 * 生产环境为 null，验证码只写入服务端日志/邮件通道。
 */
public record EmailCodeResult(int expiresIn, int cooldown, String devCode) {
}
