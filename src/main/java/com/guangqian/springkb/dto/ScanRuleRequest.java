package com.guangqian.springkb.dto;

/**
 * 脱敏规则新增/修改请求：label 规则名称、pattern 正则（内置规则不可改）、enabled 启用开关。
 */
public record ScanRuleRequest(String label, String pattern, Boolean enabled) {
}
