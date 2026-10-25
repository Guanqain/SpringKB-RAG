package com.guangqian.springkb.controller;

import java.util.List;

import com.guangqian.springkb.common.ApiResponse;
import com.guangqian.springkb.common.NotFoundException;
import com.guangqian.springkb.dto.AppendMessageRequest;
import com.guangqian.springkb.dto.ConversationDetail;
import com.guangqian.springkb.dto.ConversationSummary;
import com.guangqian.springkb.dto.CreateConversationRequest;
import com.guangqian.springkb.me.ConversationService;
import com.guangqian.springkb.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 问答历史会话接口（需登录）：列表、新建、详情、重命名、删除、追加一轮问答。
 * 用户只能访问 username 匹配的会话（服务层每个查询都带 username 条件）。
 */
@RestController
@RequestMapping("/api/me/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public ApiResponse<List<ConversationSummary>> list(Authentication authentication) {
        String username = CurrentUser.username(authentication);
        if (username == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }
        return ApiResponse.ok(conversationService.list(username));
    }

    @PostMapping
    public ApiResponse<ConversationDetail> create(@RequestBody(required = false) CreateConversationRequest req,
            Authentication authentication) {
        String username = CurrentUser.username(authentication);
        if (username == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }
        return ApiResponse.ok(conversationService.create(username, req == null ? null : req.title()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ConversationDetail> detail(@PathVariable long id, Authentication authentication) {
        String username = CurrentUser.username(authentication);
        if (username == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }
        try {
            return ApiResponse.ok(conversationService.detail(username, id));
        } catch (NotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PatchMapping("/{id}")
    public ApiResponse<ConversationDetail> rename(@PathVariable long id,
            @RequestBody(required = false) CreateConversationRequest req, Authentication authentication) {
        String username = CurrentUser.username(authentication);
        if (username == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }
        try {
            return ApiResponse.ok(conversationService.rename(username, id, req == null ? null : req.title()));
        } catch (NotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id, Authentication authentication) {
        String username = CurrentUser.username(authentication);
        if (username == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }
        try {
            conversationService.delete(username, id);
            return ApiResponse.ok();
        } catch (NotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<ConversationDetail> append(@PathVariable long id,
            @RequestBody(required = false) AppendMessageRequest req, Authentication authentication) {
        String username = CurrentUser.username(authentication);
        if (username == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }
        if (req == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        try {
            return ApiResponse.ok(conversationService.append(username, id, req));
        } catch (NotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
