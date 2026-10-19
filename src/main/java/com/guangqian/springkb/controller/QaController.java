package com.guangqian.springkb.controller;

import java.util.List;
import java.util.Map;

import com.guangqian.springkb.common.ApiResponse;
import com.guangqian.springkb.dto.AskRequest;
import com.guangqian.springkb.rag.QaResult;
import com.guangqian.springkb.rag.QaService;
import com.guangqian.springkb.rag.RagSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 问答接口：GET /api/qa/search（版本过滤检索，调试/演示，§5.2）与 POST /api/qa/ask（检索增强问答，§5.2）。
 */
@RestController
@RequestMapping("/api/qa")
public class QaController {

    private static final int MAX_TOP_K = 50;

    private static final int ASK_DEFAULT_TOP_K = 5;

    private static final int ASK_MAX_TOP_K = 10;

    private final RagSearchService ragSearchService;

    private final QaService qaService;

    public QaController(RagSearchService ragSearchService, QaService qaService) {
        this.ragSearchService = ragSearchService;
        this.qaService = qaService;
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
        }
    }
}
