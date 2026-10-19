package com.guangqian.springkb.dto;

/**
 * 登录/注册成功后的返回：JWT + 用户信息
 */
public record AuthResponse(String token, UserDto user) {
}
