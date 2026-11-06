package com.guangqian.springkb.dto;

/**
 * 管理端系统设置更新（PUT /api/admin/settings）。
 * allowAnonymous 为 null 时保持原值；teamName 为空时回落「我的团队」。
 */
public record AppSettingsRequest(String teamName, Boolean allowAnonymous) {
}
