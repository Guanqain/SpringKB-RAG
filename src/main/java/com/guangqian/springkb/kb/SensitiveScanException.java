package com.guangqian.springkb.kb;

/**
 * 脱敏扫描命中敏感信息：发布/编辑/可见性切换被拦截。
 * 异常携带扫描结果，控制器据此返回命中项列表供前端展示。
 */
public class SensitiveScanException extends RuntimeException {

    private final transient SensitiveScanService.Result result;

    public SensitiveScanException(SensitiveScanService.Result result) {
        super("文档包含敏感信息，请修改后再发布");
        this.result = result;
    }

    public SensitiveScanService.Result result() {
        return result;
    }
}
