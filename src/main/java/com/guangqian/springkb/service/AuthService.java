package com.guangqian.springkb.service;

import com.guangqian.springkb.dto.AuthResponse;
import com.guangqian.springkb.dto.UserDto;
import com.guangqian.springkb.entity.User;
import com.guangqian.springkb.repository.UserRepository;
import com.guangqian.springkb.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务：注册（BCrypt 加密）、登录（校验 + 签发 JWT）、当前用户
 */
@Service
public class AuthService {

    private static final String DEFAULT_ROLE = "ADMIN";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponse register(String username, String password) {
        validateCredentials(username, password);
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("用户名已存在");
        }
        User user = userRepository.insert(username, passwordEncoder.encode(password), DEFAULT_ROLE);
        return new AuthResponse(jwtUtil.generateToken(user), UserDto.from(user));
    }

    public AuthResponse login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return new AuthResponse(jwtUtil.generateToken(user), UserDto.from(user));
    }

    public UserDto me(String username) {
        return userRepository.findByUsername(username)
                .map(UserDto::from)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    private void validateCredentials(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (username.length() < 3 || username.length() > 32) {
            throw new IllegalArgumentException("用户名长度需在 3-32 个字符之间");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码长度至少 6 位");
        }
    }
}
