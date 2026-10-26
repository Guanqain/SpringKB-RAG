package com.guangqian.springkb.controller;

import com.guangqian.springkb.common.ApiResponse;
import com.guangqian.springkb.dto.AuthResponse;
import com.guangqian.springkb.dto.CodeLoginRequest;
import com.guangqian.springkb.dto.CodeLoginResult;
import com.guangqian.springkb.dto.EmailCodeRequest;
import com.guangqian.springkb.dto.EmailCodeResult;
import com.guangqian.springkb.dto.EmailRegisterRequest;
import com.guangqian.springkb.dto.LoginRequest;
import com.guangqian.springkb.dto.PasswordCodeRequest;
import com.guangqian.springkb.dto.PasswordResetRequest;
import com.guangqian.springkb.dto.RegisterRequest;
import com.guangqian.springkb.dto.UserDto;
import com.guangqian.springkb.security.CurrentUser;
import com.guangqian.springkb.service.AuthService;
import com.guangqian.springkb.service.EmailCodeService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：邮箱验证码 / 注册 / 密码登录 / 验证码登录 / 当前用户。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailCodeService emailCodeService;

    public AuthController(AuthService authService, EmailCodeService emailCodeService) {
        this.authService = authService;
        this.emailCodeService = emailCodeService;
    }

    /** 发送邮箱验证码：purpose = register / login / bind。 */
    @PostMapping("/email-code")
    public ApiResponse<EmailCodeResult> sendEmailCode(@RequestBody(required = false) EmailCodeRequest req) {
        if (req == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        try {
            return ApiResponse.ok(emailCodeService.send(req.email(), req.purpose()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@RequestBody(required = false) RegisterRequest req) {
        if (req == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        try {
            return ApiResponse.ok(authService.register(req.username(), req.email(), req.password(), req.code()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody(required = false) LoginRequest req) {
        if (req == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        try {
            return ApiResponse.ok(authService.login(req.username(), req.password()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 验证码登录：{ email, code }。邮箱未注册时返回 needs_profile，由前端引导补充信息。 */
    @PostMapping("/login/code")
    public ApiResponse<CodeLoginResult> loginByCode(@RequestBody(required = false) CodeLoginRequest req) {
        if (req == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        try {
            return ApiResponse.ok(authService.loginByCode(req.email(), req.code()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 忘记密码：发送重置验证码。无论邮箱是否注册都返回一致响应，避免账号存在性探测。
     */
    @PostMapping("/password/code")
    public ApiResponse<EmailCodeResult> sendPasswordCode(@RequestBody(required = false) PasswordCodeRequest req) {
        if (req == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        try {
            return ApiResponse.ok(authService.requestPasswordReset(req.email()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 忘记密码：用邮箱验证码重置密码（验证码一次性使用，新密码至少 6 位）。 */
    @PostMapping("/password/reset")
    public ApiResponse<?> resetPassword(@RequestBody(required = false) PasswordResetRequest req) {
        if (req == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        try {
            authService.resetPassword(req.email(), req.code(), req.newPassword());
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 未注册邮箱的验证码登录收尾：补全用户名与密码后建号并登录。 */
    @PostMapping("/register/email")
    public ApiResponse<AuthResponse> registerByEmail(@RequestBody(required = false) EmailRegisterRequest req) {
        if (req == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        try {
            return ApiResponse.ok(authService.registerByEmailCode(
                    req.email(), req.code(), req.username(), req.password()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 当前用户：/api/auth/** 为 permitAll，匿名访问时 Security 注入的是
     * AnonymousAuthenticationToken（isAuthenticated() 恒为 true），必须用 CurrentUser 识别，
     * 否则会以 anonymousUser 查库并返回「用户不存在」400，让前端误判为非登录失效。
     */
    @GetMapping("/me")
    public ApiResponse<UserDto> me(Authentication authentication) {
        String username = CurrentUser.username(authentication);
        if (username == null) {
            return ApiResponse.error(401, "未登录或登录已过期");
        }
        try {
            return ApiResponse.ok(authService.me(username));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
