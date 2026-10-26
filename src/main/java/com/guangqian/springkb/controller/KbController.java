package com.guangqian.springkb.controller;

import java.util.List;

import com.guangqian.springkb.common.ApiResponse;
import com.guangqian.springkb.common.NotFoundException;
import com.guangqian.springkb.dto.CategoryDto;
import com.guangqian.springkb.dto.KbDocCard;
import com.guangqian.springkb.dto.KbDocDetail;
import com.guangqian.springkb.dto.KbOverview;
import com.guangqian.springkb.dto.KbTreeNode;
import com.guangqian.springkb.dto.PageResult;
import com.guangqian.springkb.kb.KbService;
import com.guangqian.springkb.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库导航接口（用户端公开只读）：GET /api/kb/overview | /tree | /docs | /doc/{id}。
 * 鉴权：/api/kb/** 在 SecurityConfig 中 permitAll（与 /api/qa/** 同策略）；
 * /doc/{id} 会顺带识别登录用户以返回点赞/收藏状态，匿名访问不影响可读性。
 */
@RestController
@RequestMapping("/api/kb")
public class KbController {

    private final KbService kbService;

    public KbController(KbService kbService) {
        this.kbService = kbService;
    }

    @GetMapping("/overview")
    public ApiResponse<KbOverview> overview(Authentication authentication) {
        return ApiResponse.ok(kbService.overview(CurrentUser.username(authentication)));
    }

    @GetMapping("/tree")
    public ApiResponse<List<KbTreeNode>> tree(Authentication authentication) {
        return ApiResponse.ok(kbService.tree(CurrentUser.username(authentication)));
    }

    /** 文档卡分页：page 从 1 起、size 钳制 1~100（默认 12）。 */
    @GetMapping("/docs")
    public ApiResponse<PageResult<KbDocCard>> docs(
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            Authentication authentication) {
        return ApiResponse.ok(kbService.docsPage(product, version, source, keyword, page, size,
                CurrentUser.username(authentication)));
    }

    /** 技术分类及各自版本（用户端筛选元数据）。 */
    @GetMapping("/categories")
    public ApiResponse<List<CategoryDto>> categories() {
        return ApiResponse.ok(kbService.categories());
    }

    @GetMapping("/doc/{id}")
    public ApiResponse<KbDocDetail> doc(@PathVariable String id, Authentication authentication) {
        try {
            return ApiResponse.ok(kbService.doc(id, CurrentUser.username(authentication)));
        } catch (NotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }
}