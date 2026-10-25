package com.guangqian.springkb.dto;

/**
 * 会话列表项（GET /api/me/conversations）。
 */
public record ConversationSummary(long id, String title, int messageCount, String updatedAt) {
}
