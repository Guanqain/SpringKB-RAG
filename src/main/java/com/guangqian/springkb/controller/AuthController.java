package com.guangqian.springkb.controller;

import com.guangqian.springkb.common.ApiResponse;
import com.guangqian.springkb.dto.AuthResponse;
import com.guangqian.springkb.dto.LoginRequest;
import com.guangqian.springkb.dto.RegisterRequest;
import com.guangqian.springkb.dto.UserDto;
import com.guangqian.springkb.service.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册 / 登录 / 当前用户
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@RequestBody RegisterRequest req) {
        try {
            return ApiResponse.ok(authService.register(req.username(), req.password()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody LoginRequest req) {
        try {
            return ApiResponse.ok(authService.login(req.username(), req.password()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @GetMapping("/me")
    public ApiResponse<UserDto> me(Authentication authentication) {
        try {
            return ApiResponse.ok(authService.me(authentication.getName()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
