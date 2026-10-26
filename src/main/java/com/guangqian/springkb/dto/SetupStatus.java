package com.guangqian.springkb.dto;

/**
 * 初始化向导状态（GET /api/setup/status，公开）。
 * initialized=true 表示已存在用户（系统已初始化），此时不允许再次初始化。
 */
public record SetupStatus(boolean initialized, long userCount, String teamName, boolean allowAnonymous) {
}
