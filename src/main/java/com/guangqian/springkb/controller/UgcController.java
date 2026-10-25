package com.guangqian.springkb.controller;

import com.guangqian.springkb.common.ApiResponse;
import com.guangqian.springkb.dto.UgcPublishRequest;
import com.guangqian.springkb.kb.UgcService;
import com.guangqian.springkb.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模拟 UGC 文档上传接口：POST /api/ugc/documents。
 * 鉴权：/api/ugc/** 走默认 authenticated（写文档页须登录），JWT 由前端拦截器注入；
 * 发布成功后文档 author 记录为当前用户。
 */
@RestController
@RequestMapping("/api/ugc")
public class UgcController {

    private final UgcService ugcService;

    public UgcController(UgcService ugcService) {
        this.ugcService = ugcService;
    }

    @PostMapping("/documents")
    public ApiResponse<UgcService.UgcPublishResult> publish(@RequestBody(required = false) UgcPublishRequest req,
            Authentication authentication) {
        if (req == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        String author = CurrentUser.username(authentication);
        if (author == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }
        try {
            return ApiResponse.ok(ugcService.publish(req, author));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}