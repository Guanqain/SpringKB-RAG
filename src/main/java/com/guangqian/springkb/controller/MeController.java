package com.guangqian.springkb.controller;

import java.util.List;

import com.guangqian.springkb.common.ApiResponse;
import com.guangqian.springkb.common.NotFoundException;
import com.guangqian.springkb.dto.BindEmailRequest;
import com.guangqian.springkb.dto.ChangePasswordRequest;
import com.guangqian.springkb.dto.InteractionResult;
import com.guangqian.springkb.dto.KbDocCard;
import com.guangqian.springkb.dto.UserDto;
import com.guangqian.springkb.kb.KbService;
import com.guangqian.springkb.me.InteractionService;
import com.guangqian.springkb.me.NotificationService;
import com.guangqian.springkb.security.CurrentUser;
import com.guangqian.springkb.service.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人中心接口（需登录）：我写过的文档、我的收藏、点赞/收藏操作、修改密码、绑定邮箱。
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final KbService kbService;
    private final InteractionService interactionService;
    private final AuthService authService;
    private final NotificationService notificationService;

    public MeController(KbService kbService, InteractionService interactionService, AuthService authService,
                        NotificationService notificationService) {
        this.kbService = kbService;
        this.interactionService = interactionService;
        this.authService = authService;
        this.notificationService = notificationService;
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

    /** POST /api/me/password：修改密码（校验旧密码）。 */
    @PostMapping("/password")
    public ApiResponse<UserDto> changePassword(@RequestBody(required = false) ChangePasswordRequest req,
            Authentication authentication) {
        String username = CurrentUser.username(authentication);
        if (username == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }
        if (req == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        try {
            return ApiResponse.ok(authService.changePassword(username, req.oldPassword(), req.newPassword()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** POST /api/me/email：绑定/更换邮箱（验证码 purpose=bind）。 */
    @PostMapping("/email")
    public ApiResponse<UserDto> bindEmail(@RequestBody(required = false) BindEmailRequest req,
            Authentication authentication) {
        String username = CurrentUser.username(authentication);
        if (username == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }
        if (req == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        try {
            return ApiResponse.ok(authService.bindEmail(username, req.email(), req.code()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
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

    /** GET /api/me/notifications：站内通知（审核结果）+ 未读数。 */
    @GetMapping("/notifications")
    public ApiResponse<?> notifications(@RequestParam(required = false) Integer limit,
            Authentication authentication) {
        String username = CurrentUser.username(authentication);
        if (username == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }
        return ApiResponse.ok(notificationService.list(username, limit == null ? 20 : limit));
    }

    /** POST /api/me/notifications/{id}/read：单条已读。 */
    @PostMapping("/notifications/{id}/read")
    public ApiResponse<?> markNotificationRead(@PathVariable long id, Authentication authentication) {
        String username = CurrentUser.username(authentication);
        if (username == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }
        try {
            notificationService.markRead(username, id);
            return ApiResponse.ok();
        } catch (NotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    /** POST /api/me/notifications/read-all：全部已读，返回更新条数。 */
    @PostMapping("/notifications/read-all")
    public ApiResponse<Integer> markAllNotificationsRead(Authentication authentication) {
        String username = CurrentUser.username(authentication);
        if (username == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }
        return ApiResponse.ok(notificationService.markAllRead(username));
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
