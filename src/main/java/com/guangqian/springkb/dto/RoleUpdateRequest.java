package com.guangqian.springkb.dto;

/**
 * 成员角色变更（PATCH /api/admin/members/{id}/role）：role 仅支持 ADMIN / MEMBER。
 */
public record RoleUpdateRequest(String role) {
}
