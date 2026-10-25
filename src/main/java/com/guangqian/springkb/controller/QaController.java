package com.guangqian.springkb.controller;

import java.util.List;
import java.util.Map;

import com.guangqian.springkb.common.ApiResponse;
import com.guangqian.springkb.dto.AskRequest;
import com.guangqian.springkb.rag.ModelUnavailableException;
import com.guangqian.springkb.rag.QaResult;
import com.guangqian.springkb.rag.QaService;
import com.guangqian.springkb.rag.RagSearchService;
import com.guangqian.springkb.rag.RagTraceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 问答接口：GET /api/qa/search（版本过滤检索，调试/演示，§5.2）、POST /api/qa/ask（检索增强问答，§5.2）、
 * GET /api/qa/debug（检索链路诊断，不调模型）。
 */
@RestController
@RequestMapping("/api/qa")
public class QaController {

    private static final int MAX_TOP_K = 50;

    private static final int ASK_DEFAULT_TOP_K = 5;

    private static final int ASK_MAX_TOP_K = 10;

    private final RagSearchService ragSearchService;

    private final QaService qaService;

    private final RagTraceService ragTraceService;

    public QaController(RagSearchService ragSearchService, QaService qaService, RagTraceService ragTraceService) {
        this.ragSearchService = ragSearchService;
        this.qaService = qaService;
        this.ragTraceService = ragTraceService;
    }

    /** GET /api/qa/search?q=...&version=...&topK=...：q 必填，version 可选，topK 默认 15、钳制 1~50。 */
    @GetMapping("/search")
    public ApiResponse<List<Map<String, Object>>> search(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "topK", defaultValue = "15") int topK) {
        if (q == null || q.isBlank()) {
            return ApiResponse.error(400, "参数 q 必填");
        }
        int k = Math.max(1, Math.min(MAX_TOP_K, topK));
        try {
            return ApiResponse.ok(ragSearchService.search(q, version, k));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** GET /api/qa/versions：active 文档的去重版本列表（用户端版本下拉元数据，公开）。 */
    @GetMapping("/versions")
    public ApiResponse<List<String>> versions() {
        return ApiResponse.ok(qaService.listActiveVersions());
    }

    /** POST /api/qa/ask（§5.2）：question 必填；version 可选；topK 缺省 5、钳制 1~10。version 非法映射 400。 */
    @PostMapping("/ask")
    public ApiResponse<QaResult> ask(@RequestBody AskRequest req) {
        if (req == null || req.question() == null || req.question().isBlank()) {
            return ApiResponse.error(400, "参数 question 必填");
        }
        int k = req.topK() == null ? ASK_DEFAULT_TOP_K : Math.max(1, Math.min(ASK_MAX_TOP_K, req.topK()));
        try {
            return ApiResponse.ok(qaService.ask(req.question(), req.version(), k));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (ModelUnavailableException e) {
            // 模型服务不可用（超时/连接失败）：与「知识库无相关内容」区分，前端可提示重试
            return ApiResponse.error(503, "模型服务暂时不可用，请稍后重试");
        }
    }

    /**
     * GET /api/qa/debug?q=...&version=...&topK=...：检索链路诊断（可观测性）。
     *
     * <p>只跑检索侧（向量化 → 初检 → 阈值 → 重排 → 回源），**不调用 Chat 模型**，
     * 因此无 token 消耗与副作用；返回每段的状态、耗时与数据，同时控制台输出同 traceId 的日志。</p>
     */
    @GetMapping("/debug")
    public ApiResponse<Map<String, Object>> debug(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "topK", defaultValue = "5") int topK) {
        if (q == null || q.isBlank()) {
            return ApiResponse.error(400, "参数 q 必填");
        }
        int k = Math.max(1, Math.min(ASK_MAX_TOP_K, topK));
        try {
            return ApiResponse.ok(ragTraceService.trace(q, version, k));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
