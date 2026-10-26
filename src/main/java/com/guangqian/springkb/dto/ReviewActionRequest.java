package com.guangqian.springkb.dto;

/**
 * 审核动作请求：note 为可选备注；驳回时必填（作为作者可见的驳回理由）。
 */
public record ReviewActionRequest(String note) {
}
