package com.guangqian.springkb.ingestion;

/**
 * 批量导入汇总：imported 实际入库数、skipped 幂等跳过数、failed 单文档失败数。
 */
public record ImportSummary(int imported, int skipped, int failed) {

    public int total() {
        return imported + skipped + failed;
    }
}
