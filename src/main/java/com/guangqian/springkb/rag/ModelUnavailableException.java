package com.guangqian.springkb.rag;

/**
 * 模型服务不可用异常：Chat 调用超时、连接失败等运行时异常统一转换，
 * 由 Controller 映射为 503，与「知识库无相关内容」的兜底结果区分开。
 */
public class ModelUnavailableException extends RuntimeException {

    public ModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
