package com.guangqian.springkb.controller;

import com.guangqian.springkb.common.ApiResponse;
import com.guangqian.springkb.dto.AuthResponse;
import com.guangqian.springkb.dto.SetupInitRequest;
import com.guangqian.springkb.dto.SetupStatus;
import com.guangqian.springkb.service.SetupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 初始化向导接口（公开）：GET /api/setup/status、POST /api/setup/init。
 * init 仅在系统无用户时成功，成功后返回管理员 JWT 以便前端自动登录。
 */
@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private final SetupService setupService;

    public SetupController(SetupService setupService) {
        this.setupService = setupService;
    }

    @GetMapping("/status")
    public ApiResponse<SetupStatus> status() {
        return ApiResponse.ok(setupService.status());
    }

    @PostMapping("/init")
    public ApiResponse<AuthResponse> init(@RequestBody(required = false) SetupInitRequest req) {
        try {
            return ApiResponse.ok(setupService.init(req));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
