package com.guangqian.springkb.dto;

import java.util.List;

/**
 * 分页结果包装：page 从 1 开始，size 钳制在 1~200。
 */
public record PageResult<T>(List<T> list, long total, int page, int size) {
}
