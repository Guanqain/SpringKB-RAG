package com.guangqian.springkb.dto;

/**
 * 邮箱验证码直接建号：验证码登录发现邮箱未注册时，用户补全用户名与密码后提交。
 * code 为已发送且未使用的 login 验证码。
 */
public record EmailRegisterRequest(String email, String code, String username, String password) {
}
