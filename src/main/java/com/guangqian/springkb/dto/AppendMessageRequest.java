package com.guangqian.springkb.dto;

import java.util.List;

import com.guangqian.springkb.rag.SourceRef;

/**
 * 追加一轮问答（POST /api/me/conversations/{id}/messages）。
 */
public record AppendMessageRequest(String question, String answer, Boolean found, String version,
                                   List<SourceRef> sources) {
}
