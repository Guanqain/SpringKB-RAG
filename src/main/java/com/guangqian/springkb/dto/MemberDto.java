package com.guangqian.springkb.dto;

/**
 * 管理端成员列表行（GET /api/admin/members），不含密码哈希。
 */
public record MemberDto(long id, String username, String email, String role, String createdAt) {
}
