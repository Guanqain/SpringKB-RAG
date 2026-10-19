package com.guangqian.springkb.common;

/**
 * 资源不存在异常：由 Controller 统一映射为 404 响应。
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
