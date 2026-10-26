package com.guangqian.springkb.dto;

import java.util.List;

/**
 * 批量审核通过请求：ids 为待审文档主键列表（驳回仍需逐条填写理由）。
 */
public record ReviewBatchRequest(List<String> ids) {
}
