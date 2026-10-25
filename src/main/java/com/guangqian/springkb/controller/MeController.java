package com.guangqian.springkb.controller;

import java.util.List;

import com.guangqian.springkb.common.ApiResponse;
import com.guangqian.springkb.common.NotFoundException;
import com.guangqian.springkb.dto.InteractionResult;
import com.guangqian.springkb.dto.KbDocCard;
import com.guangqian.springkb.kb.KbService;
import com.guangqian.springkb.me.InteractionService;
import com.guangqian.springkb.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人中心接口（需登录）：我写过的文档、我的收藏、点赞/收藏操作。
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final KbService kbService;
    private final InteractionService interactionService;

    public MeController(KbService kbService, InteractionService interactionService) {
        this.kbService = kbService;
        this.interactionService = interactionService;
    }

    /** GET /api/me/documents：当前用户发布的 UGC 文档。 */
    @GetMapping("/documents")
    public ApiResponse<List<KbDocCard>> documents(Authentication authentication) {
        String username = CurrentUser.username(authentication);
        if (username == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }
        return ApiResponse.ok(kbService.myDocs(username));
    }

    /** GET /api/me/favorites：当前用户收藏的文档。 */
    @GetMapping("/favorites")
    public ApiResponse<List<KbDocCard>> favorites(Authentication authentication) {
        String username = CurrentUser.username(authentication);
        if (username == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }
        return ApiResponse.ok(kbService.favoriteDocs(username));
    }

    @PostMapping("/docs/{docId}/like")
    public ApiResponse<InteractionResult> like(@PathVariable String docId, Authentication authentication) {
        return toggle(docId, authentication, true, false);
    }

    @DeleteMapping("/docs/{docId}/like")
    public ApiResponse<InteractionResult> unlike(@PathVariable String docId, Authentication authentication) {
        return toggle(docId, authentication, false, false);
    }

    @PostMapping("/docs/{docId}/favorite")
    public ApiResponse<InteractionResult> favorite(@PathVariable String docId, Authentication authentication) {
        return toggle(docId, authentication, true, true);
    }

    @DeleteMapping("/docs/{docId}/favorite")
    public ApiResponse<InteractionResult> unfavorite(@PathVariable String docId, Authentication authentication) {
        return toggle(docId, authentication, false, true);
    }

    private ApiResponse<InteractionResult> toggle(String docId, Authentication authentication,
            boolean on, boolean favorite) {
        String username = CurrentUser.username(authentication);
        if (username == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }
        try {
            InteractionResult result;
            if (favorite) {
                result = on ? interactionService.favorite(docId, username)
                        : interactionService.unfavorite(docId, username);
            } else {
                result = on ? interactionService.like(docId, username)
                        : interactionService.unlike(docId, username);
            }
            return ApiResponse.ok(result);
        } catch (NotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }
}
