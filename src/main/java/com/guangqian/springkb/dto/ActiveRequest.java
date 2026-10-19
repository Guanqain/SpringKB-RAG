package com.guangqian.springkb.dto;

/**
 * 版本状态切换请求：{ "active": true/false }；用 Boolean 包装以区分「缺字段」与「false」。
 */
public record ActiveRequest(Boolean active) {
}
