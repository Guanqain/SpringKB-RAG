package com.guangqian.springkb.dto;

/**
 * 模拟 UGC 文档上传请求（POST /api/ugc/documents）。
 * 可见性（team/private）属 C4，后端 schema 尚无 visibility 列，暂不落库。
 */
public record UgcPublishRequest(String title, String markdown, String version, String section) {
}