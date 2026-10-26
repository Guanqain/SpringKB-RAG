package com.guangqian.springkb.entity;

/**
 * 用户实体（对应 kb_user 表，密码只存 BCrypt 哈希，绝不存明文）。
 * email 允许为空（历史用户可在个人中心补绑）。
 */
public record User(long id, String username, String email, String passwordHash, String role) {
}
