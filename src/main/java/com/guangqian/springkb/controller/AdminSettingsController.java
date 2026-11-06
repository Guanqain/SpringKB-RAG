package com.guangqian.springkb.controller;

import com.guangqian.springkb.common.ApiResponse;
import com.guangqian.springkb.dto.AppSettingsDto;
import com.guangqian.springkb.dto.AppSettingsRequest;
import com.guangqian.springkb.service.AppSettingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端系统设置（需 ADMIN 角色，由 SecurityConfig 的 /api/admin/** 保证）：
 * 团队名称与匿名访问开关，保存后立即生效。
 */
@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingsController {

    private final AppSettingService appSettingService;

    public AdminSettingsController(AppSettingService appSettingService) {
        this.appSettingService = appSettingService;
    }

    @GetMapping
    public ApiResponse<AppSettingsDto> get() {
        return ApiResponse.ok(current());
    }

    @PutMapping
    public ApiResponse<AppSettingsDto> update(@RequestBody(required = false) AppSettingsRequest req) {
        try {
            if (req == null) {
                throw new IllegalArgumentException("请求体不能为空");
            }
            String teamName = req.teamName() == null ? "" : req.teamName().trim();
            if (teamName.isEmpty()) {
                teamName = "我的团队";
            }
            if (teamName.length() > 40) {
                throw new IllegalArgumentException("团队名称最多 40 个字符");
            }
            appSettingService.put(AppSettingService.KEY_TEAM_NAME, teamName);
            if (req.allowAnonymous() != null) {
                appSettingService.setAllowAnonymous(req.allowAnonymous());
            }
            return ApiResponse.ok(current());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    private AppSettingsDto current() {
        return new AppSettingsDto(appSettingService.getTeamName(), appSettingService.allowAnonymous());
    }
}
