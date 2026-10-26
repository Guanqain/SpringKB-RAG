package com.guangqian.springkb.dto;

/**
 * 修改密码请求（POST /api/me/password，需登录，校验旧密码）。
 */
public record ChangePasswordRequest(String oldPassword, String newPassword) {
}
