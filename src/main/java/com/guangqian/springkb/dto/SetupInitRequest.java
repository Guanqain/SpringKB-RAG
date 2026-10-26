package com.guangqian.springkb.dto;

/**
 * 初始化向导提交（POST /api/setup/init，仅在系统无任何用户时可用）。
 * 创建首个 ADMIN 账号并写入团队名 / 匿名访问开关。
 */
public record SetupInitRequest(String teamName, String adminUsername, String adminEmail,
                               String adminPassword, String code, Boolean allowAnonymous) {
}
