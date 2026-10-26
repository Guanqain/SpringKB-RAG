package com.guangqian.springkb.controller;

import com.guangqian.springkb.common.ApiResponse;
import com.guangqian.springkb.common.NotFoundException;
import com.guangqian.springkb.dto.UgcPublishRequest;
import com.guangqian.springkb.dto.VisibilityRequest;
import com.guangqian.springkb.kb.SensitiveScanException;
import com.guangqian.springkb.kb.SensitiveScanService;
import com.guangqian.springkb.kb.UgcService;
import com.guangqian.springkb.security.CurrentUser;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * UGC 文档接口：
 * POST /api/ugc/documents（Markdown 发布）、POST /api/ugc/documents/upload（.txt/.md 文件发布，≤10MB）、
 * POST /api/ugc/scan（发布前脱敏预检）、PUT /api/ugc/documents/{id}（编辑并重新提交）、
 * PATCH /api/ugc/documents/{id}/visibility（作者或管理员切换可见性）、
 * DELETE /api/ugc/documents/{id}（作者删除自己的文档）。
 * 鉴权：/api/ugc/** 走默认 authenticated（写文档页须登录），JWT 由前端拦截器注入。
 */
@RestController
@RequestMapping("/api/ugc")
public class UgcController {

    private static final String SCAN_BLOCKED_MESSAGE = "文档包含敏感信息，请修改后再发布";

    private final UgcService ugcService;

    public UgcController(UgcService ugcService) {
        this.ugcService = ugcService;
    }

    @PostMapping("/documents")
    public ApiResponse<?> publish(@RequestBody(required = false) UgcPublishRequest req,
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
        } catch (SensitiveScanException e) {
            return blocked(e);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 文件上传：multipart/form-data，file 必填，其余字段同发布。 */
    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String visibility,
            Authentication authentication) {
        String author = CurrentUser.username(authentication);
        if (author == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }
        try {
            return ApiResponse.ok(ugcService.publishFile(file, title, product, version, section, visibility, author));
        } catch (SensitiveScanException e) {
            return blocked(e);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 发布前脱敏预检：只扫描不落库；私有文档返回 clean。 */
    @PostMapping("/scan")
    public ApiResponse<SensitiveScanService.Result> scan(@RequestBody(required = false) UgcPublishRequest req,
            Authentication authentication) {
        String username = CurrentUser.username(authentication);
        if (username == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }
        if (req == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        try {
            return ApiResponse.ok(ugcService.scan(req, username));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 编辑并重新提交：删除旧文档后重新入库，团队文档重新进入待审。 */
    @PutMapping("/documents/{id}")
    public ApiResponse<?> update(@PathVariable String id,
            @RequestBody(required = false) UgcPublishRequest req,
            Authentication authentication) {
        String author = CurrentUser.username(authentication);
        if (author == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }
        if (req == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        try {
            return ApiResponse.ok(ugcService.update(id, req, author));
        } catch (SensitiveScanException e) {
            return blocked(e);
        } catch (NotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PatchMapping("/documents/{id}/visibility")
    public ApiResponse<?> setVisibility(@PathVariable String id,
            @RequestBody(required = false) VisibilityRequest body,
            Authentication authentication) {
        String author = CurrentUser.username(authentication);
        if (author == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }
        if (body == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        try {
            return ApiResponse.ok(ugcService.setVisibility(id, body.visibility(), author, admin));
        } catch (SensitiveScanException e) {
            return blocked(e);
        } catch (NotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/documents/{id}")
    public ApiResponse<?> delete(@PathVariable String id, Authentication authentication) {
        String author = CurrentUser.username(authentication);
        if (author == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }
        try {
            ugcService.delete(id, author);
            return ApiResponse.ok();
        } catch (NotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    private static ApiResponse<?> blocked(SensitiveScanException e) {
        return new ApiResponse<>(422, SCAN_BLOCKED_MESSAGE, e.result());
    }
}
