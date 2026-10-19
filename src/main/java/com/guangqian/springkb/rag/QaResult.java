package com.guangqian.springkb.rag;

import java.util.List;

/**
 * 问答结果（§5.2）：found=false 时 answer 固定为「知识库中未找到相关答案」，sources 为空。
 */
public record QaResult(boolean found, String answer, List<SourceRef> sources) {
}
