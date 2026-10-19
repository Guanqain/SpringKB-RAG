package com.guangqian.springkb.ingestion;

/**
 * Token 估算器：max(1, round(字符数 / 4))，中英文统一，纯函数便于测试。
 * 用于分块预算管控与 token_count 统计（够用即可，不做语言分词）。
 */
public final class TokenEstimator {

    private TokenEstimator() {
    }

    public static int estimate(String text) {
        if (text == null) {
            return 0;
        }
        return Math.max(1, (int) Math.round(text.length() / 4.0));
    }
}
