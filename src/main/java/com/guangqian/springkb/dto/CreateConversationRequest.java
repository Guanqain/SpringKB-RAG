package com.guangqian.springkb.dto;

/**
 * 新建会话（POST /api/me/conversations）；title 为空时由服务端取默认值。
 */
public record CreateConversationRequest(String title) {
}
