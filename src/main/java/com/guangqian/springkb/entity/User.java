package com.guangqian.springkb.entity;

/**
 * 用户实体（对应 kb_user 表，密码只存 BCrypt 哈希，绝不存明文）
 */
public record User(long id, String username, String passwordHash, String role) {
}
