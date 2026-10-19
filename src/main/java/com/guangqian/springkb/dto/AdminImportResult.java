package com.guangqian.springkb.dto;

/**
 * 管理端导入结果：imported 本次实际入库 / skipped 幂等命中或空文档跳过，与前端 ImportResult 契约一致。
 */
public record AdminImportResult(boolean imported, boolean skipped, String docId, int chunkCount) {

    public static AdminImportResult imported(String docId, int chunkCount) {
        return new AdminImportResult(true, false, docId, chunkCount);
    }

    public static AdminImportResult skipped(String docId) {
        return new AdminImportResult(false, true, docId, 0);
    }
}
