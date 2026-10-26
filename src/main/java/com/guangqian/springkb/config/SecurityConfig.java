package com.guangqian.springkb.config;

import com.guangqian.springkb.common.ApiResponse;
import com.guangqian.springkb.security.JwtAuthFilter;
import com.guangqian.springkb.security.JwtUtil;
import com.guangqian.springkb.service.AppSettingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * Spring Security 配置：
 * - 无状态会话（JWT）
 * - 注册/登录/初始化向导与公开检索接口放行，管理接口要求 ADMIN 角色，其余接口需认证
 * - 未认证返回统一 JSON 401，越权返回统一 JSON 403
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Spring Boot 4 内置 Jackson 3（tools.jackson），自动配置了 JsonMapper bean
    private final JsonMapper jsonMapper;
    private final AppSettingService appSettingService;

    public SecurityConfig(JsonMapper jsonMapper, AppSettingService appSettingService) {
        this.jsonMapper = jsonMapper;
        this.appSettingService = appSettingService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtUtil jwtUtil) {
        return new JwtAuthFilter(jwtUtil);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthFilter jwtAuthFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 认证与初始化向导：未登录可用（初始化仅在无用户时成功，见 SetupService）
                .requestMatchers("/api/auth/**", "/api/setup/**").permitAll()
                // 管理接口：必须 ADMIN 角色（角色来自 JWT 快照，变更角色后需重新登录）
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // 用户侧公开接口：demo 冒烟始终放行；问答与知识库导航受初始化向导的「匿名访问」开关控制
                .requestMatchers("/api/demo/**").permitAll()
                .requestMatchers("/api/qa/**", "/api/kb/**").access((authentication, context) ->
                        new AuthorizationDecision(appSettingService.allowAnonymous()
                                || isLoggedIn(authentication.get())))
                // 前端页面与静态资源（容器一体化部署模式：前端产物托管于 static/）；
                // 页面级鉴权由前端路由守卫负责，接口级鉴权由上方规则保证
                .requestMatchers("/", "/index.html", "/login", "/setup", "/qa", "/kb", "/kb/**",
                        "/me", "/editor", "/admin", "/admin/**", "/documents/**",
                        "/assets/**", "/favicon.ico", "/vite.svg").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((request, response, e) ->
                        writeJson(response, 401, "未登录或登录已过期"))
                .accessDeniedHandler((request, response, e) ->
                        writeJson(response, 403, "无权限访问（需要管理员角色）")))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static boolean isLoggedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private void writeJson(jakarta.servlet.http.HttpServletResponse response, int status, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(ApiResponse.error(status, message)));
    }
}
