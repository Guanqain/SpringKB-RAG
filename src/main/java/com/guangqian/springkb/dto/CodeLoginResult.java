package com.guangqian.springkb.dto;

/**
 * 验证码登录结果：区分「已登录」与「邮箱未注册，需要补全资料」。
 *
 * <p>action = login：token/user 有效，前端直接写入登录态；
 * action = needs_profile：验证码已校验通过但邮箱没有账号，前端展示「补充用户名 + 密码」表单，
 * 提交到 /api/auth/register/email 完成建号并登录（此时验证码尚未消费，补全后才会置 used）。</p>
 */
public record CodeLoginResult(String action, String email, String token, UserDto user) {

    public static final String ACTION_LOGIN = "login";
    public static final String ACTION_NEEDS_PROFILE = "needs_profile";

    public static CodeLoginResult loggedIn(String email, AuthResponse auth) {
        return new CodeLoginResult(ACTION_LOGIN, email, auth.token(), auth.user());
    }

    public static CodeLoginResult needsProfile(String email) {
        return new CodeLoginResult(ACTION_NEEDS_PROFILE, email, null, null);
    }
}
