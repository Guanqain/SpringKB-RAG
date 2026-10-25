package com.guangqian.springkb.dto;

import java.util.List;

import com.guangqian.springkb.rag.SourceRef;

/**
 * 会话内一轮问答消息：question/answer/sources 与检索结果一一对应。
 */
public record ConversationMessage(long id, String question, String answer, boolean found,
                                  String version, List<SourceRef> sources, String createdAt) {
}
