package com.guangqian.springkb.dto;

import java.util.List;

/**
 * 会话详情（GET /api/me/conversations/{id}）：messages 按时间正序，最多保留窗口大小条。
 */
public record ConversationDetail(long id, String title, String updatedAt, List<ConversationMessage> messages) {
}
