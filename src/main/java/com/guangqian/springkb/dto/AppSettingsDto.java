package com.guangqian.springkb.dto;

/**
 * 管理端系统设置（GET /api/admin/settings）。
 */
public record AppSettingsDto(String teamName, boolean allowAnonymous) {
}
