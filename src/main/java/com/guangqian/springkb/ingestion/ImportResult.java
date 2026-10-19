package com.guangqian.springkb.ingestion;

/**
 * 单文档导入结果：IMPORTED 表示本次实际入库，SKIPPED 表示幂等命中或空文档跳过。
 */
public record ImportResult(String docId, int chunkCount, Status status) {

    public enum Status {
        IMPORTED, SKIPPED
    }

    public static ImportResult imported(String docId, int chunkCount) {
        return new ImportResult(docId, chunkCount, Status.IMPORTED);
    }

    public static ImportResult skipped(String docId) {
        return new ImportResult(docId, 0, Status.SKIPPED);
    }
}
