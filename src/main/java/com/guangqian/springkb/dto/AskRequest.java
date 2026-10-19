package com.guangqian.springkb.dto;

/**
 * POST /api/qa/ask 请求体（§5.2）：question 必填；version 可选（缺省=所有版本）；topK 可选（缺省 5，钳制 1~10）。
 */
public record AskRequest(String question, String version, Integer topK) {
}
