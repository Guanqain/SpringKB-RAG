package com.guangqian.springkb.ingestion;

/**
 * Token 估算器：按字符权重累加后取整，纯函数便于测试。
 *
 * <p>权重：CJK（汉字 / 假名 / 谚文 / 全角字符）≈ 1 token/字符；其余（拉丁字母、数字、代码符号）
 * ≈ 4 字符/token。原实现中英文统一按 4 字符/token，中文被低估约 4 倍，长章节会产出超过向量模型
 * 单条输入上限的 chunk（Spring AI {@code TokenCountBatchingStrategy} 默认 8191，预留 10% 后约 7371），
 * 导入报 {@code Tokens in a single document exceeds the maximum number of allowed input tokens}。
 */
public final class TokenEstimator {

    private static final double CJK_WEIGHT = 1.0;
    private static final double OTHER_WEIGHT = 0.25;

    private TokenEstimator() {
    }

    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, (int) Math.round(weightSum(text)));
    }

    /**
     * 未取整的权重和：逐行累加时用它做预算，避免「每行先四舍五入再累加」造成的系统性低估
     * （例如大量短代码行每行 3.25 token 取整成 3，2000 行会少算 500 token）。
     */
    static double weightSum(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            sum += weight(codePoint);
            i += Character.charCount(codePoint);
        }
        return sum;
    }

    /** 单字符 token 权重：CJK 计 1，其余计 0.25（4 字符 ≈ 1 token）。 */
    static double weight(int codePoint) {
        return isCjk(codePoint) ? CJK_WEIGHT : OTHER_WEIGHT;
    }

    /** CJK 判定：部首、CJK 标点、假名、谚文、基本/扩展/兼容汉字与全角字符。 */
    private static boolean isCjk(int cp) {
        return (cp >= 0x2E80 && cp <= 0x2EFF)
                || (cp >= 0x3000 && cp <= 0x303F)
                || (cp >= 0x3040 && cp <= 0x30FF)
                || (cp >= 0x3400 && cp <= 0x4DBF)
                || (cp >= 0x4E00 && cp <= 0x9FFF)
                || (cp >= 0xAC00 && cp <= 0xD7AF)
                || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0xFF00 && cp <= 0xFFEF)
                || (cp >= 0x20000 && cp <= 0x2FA1F);
    }
}
