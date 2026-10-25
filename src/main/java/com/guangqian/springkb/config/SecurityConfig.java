package com.guangqian.springkb.config;

import com.guangqian.springkb.common.ApiResponse;
import com.guangqian.springkb.security.JwtAuthFilter;
import com.guangqian.springkb.security.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * Spring Security 配置：
 * - 无状态会话（JWT）
 * - 注册/登录与公开检索接口放行，其余接口需认证
 * - 未认证返回统一 JSON 401
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Spring Boot 4 内置 Jackson 3（tools.jackson），自动配置了 JsonMapper bean
    private final JsonMapper jsonMapper;

    public SecurityConfig(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
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
                .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                // 用户侧公开接口：demo 冒烟 + 问答 + 知识库导航（只读）
                .requestMatchers("/api/demo/**", "/api/qa/**", "/api/kb/**").permitAll()
                // 前端页面与静态资源（容器一体化部署模式：前端产物托管于 static/）
                .requestMatchers("/", "/index.html", "/login", "/documents/**",
                        "/assets/**", "/favicon.ico", "/vite.svg").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(eh -> eh.authenticationEntryPoint((request, response, e) -> {
                response.setStatus(401);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write(
                        jsonMapper.writeValueAsString(ApiResponse.error(401, "未登录或登录已过期")));
            }))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
