package com.guangqian.springkb.service;

import com.guangqian.springkb.common.Usernames;
import com.guangqian.springkb.dto.AuthResponse;
import com.guangqian.springkb.dto.CodeLoginResult;
import com.guangqian.springkb.dto.EmailCodeResult;
import com.guangqian.springkb.dto.UserDto;
import com.guangqian.springkb.entity.User;
import com.guangqian.springkb.repository.UserRepository;
import com.guangqian.springkb.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务：注册（邮箱验证码 + BCrypt 加密）、密码/验证码登录、当前用户、改密与绑定邮箱。
 *
 * <p>角色规则：系统内第一个注册者（或 Setup 向导创建的管理员）为 ADMIN，其余为 MEMBER。</p>
 */
@Service
public class AuthService {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MEMBER = "MEMBER";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailCodeService emailCodeService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       EmailCodeService emailCodeService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.emailCodeService = emailCodeService;
    }

    /** 注册：邮箱必须通过 register 用途验证码校验；首个用户为 ADMIN，其余为 MEMBER。 */
    public AuthResponse register(String username, String email, String password, String code) {
        String normalizedUsername = validateCredentials(username, password);
        String normalizedEmail = EmailCodeService.normalizeEmail(email);
        if (userRepository.findByUsername(normalizedUsername).isPresent()) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("邮箱已被注册");
        }
        emailCodeService.verify(normalizedEmail, EmailCodeService.PURPOSE_REGISTER, code);
        String role = userRepository.count() == 0 ? ROLE_ADMIN : ROLE_MEMBER;
        User user = userRepository.insert(normalizedUsername, normalizedEmail, passwordEncoder.encode(password), role);
        return new AuthResponse(jwtUtil.generateToken(user), UserDto.from(user));
    }

    /** 密码登录：identifier 可为用户名或邮箱。 */
    public AuthResponse login(String identifier, String password) {
        User user = userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return new AuthResponse(jwtUtil.generateToken(user), UserDto.from(user));
    }

    /**
     * 验证码登录：先校验验证码（不消费）再查库——验证通过前不暴露「邮箱是否已注册」。
     * 邮箱没有账号时不报错，返回 needs_profile 让前端引导用户补全用户名与密码建号。
     */
    public CodeLoginResult loginByCode(String email, String code) {
        String normalizedEmail = EmailCodeService.normalizeEmail(email);
        long codeId = emailCodeService.check(normalizedEmail, EmailCodeService.PURPOSE_LOGIN, code);
        return userRepository.findByEmail(normalizedEmail)
                .map(user -> {
                    emailCodeService.consume(codeId);
                    return CodeLoginResult.loggedIn(normalizedEmail,
                            new AuthResponse(jwtUtil.generateToken(user), UserDto.from(user)));
                })
                .orElseGet(() -> CodeLoginResult.needsProfile(normalizedEmail));
    }

    /**
     * 未注册邮箱的「验证码登录」收尾：验证码已校验通过后，用用户名 + 密码直接建号并登录。
     * 验证码此时才消费；邮箱/用户名占用检查放在验证码校验之后，避免未验证时探测账号存在性。
     */
    public AuthResponse registerByEmailCode(String email, String code, String username, String password) {
        String normalizedUsername = validateCredentials(username, password);
        String normalizedEmail = EmailCodeService.normalizeEmail(email);
        long codeId = emailCodeService.check(normalizedEmail, EmailCodeService.PURPOSE_LOGIN, code);
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("该邮箱已注册，请直接登录");
        }
        if (userRepository.findByUsername(normalizedUsername).isPresent()) {
            throw new IllegalArgumentException("用户名已存在");
        }
        emailCodeService.consume(codeId);
        String role = userRepository.count() == 0 ? ROLE_ADMIN : ROLE_MEMBER;
        User user = userRepository.insert(normalizedUsername, normalizedEmail, passwordEncoder.encode(password), role);
        return new AuthResponse(jwtUtil.generateToken(user), UserDto.from(user));
    }

    public UserDto me(String username) {
        return userRepository.findByUsername(username)
                .map(UserDto::from)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    /** 修改密码：校验旧密码，成功后旧登录态仍有效（JWT 无状态），下次登录使用新密码。 */
    public UserDto changePassword(String username, String oldPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (oldPassword == null || !passwordEncoder.matches(oldPassword, user.passwordHash())) {
            throw new IllegalArgumentException("原密码错误");
        }
        validatePassword(newPassword);
        userRepository.updatePassword(user.id(), passwordEncoder.encode(newPassword));
        return UserDto.from(user);
    }

    /** 绑定邮箱：验证码 purpose=bind；邮箱不可已被其他账号占用。 */
    public UserDto bindEmail(String username, String email, String code) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        String normalizedEmail = EmailCodeService.normalizeEmail(email);
        userRepository.findByEmail(normalizedEmail).ifPresent(other -> {
            if (other.id() != user.id()) {
                throw new IllegalArgumentException("邮箱已被其他账号绑定");
            }
        });
        emailCodeService.verify(normalizedEmail, EmailCodeService.PURPOSE_BIND, code);
        userRepository.updateEmail(user.id(), normalizedEmail);
        return new UserDto(user.id(), user.username(), user.role(), normalizedEmail);
    }

    /**
     * 忘记密码：发送重置验证码（purpose=reset）。
     *
     * <p>安全：无论邮箱是否注册都返回一致的成功响应（TTL/冷却），避免被用来探测账号存在性；
     * 未注册邮箱不真正发信、不占用发信额度。</p>
     */
    public EmailCodeResult requestPasswordReset(String email) {
        String normalizedEmail = EmailCodeService.normalizeEmail(email);
        if (userRepository.findByEmail(normalizedEmail).isEmpty()) {
            return new EmailCodeResult(EmailCodeService.TTL_SECONDS, EmailCodeService.COOLDOWN_SECONDS, null);
        }
        return emailCodeService.send(normalizedEmail, EmailCodeService.PURPOSE_RESET);
    }

    /**
     * 重置密码：先校验（不消费）验证码，确认账号存在后消费并写入新密码哈希。
     * 验证码一次性使用；重置成功后旧密码立即失效，旧 JWT 仍在有效期内（无状态）。
     */
    public void resetPassword(String email, String code, String newPassword) {
        validatePassword(newPassword);
        String normalizedEmail = EmailCodeService.normalizeEmail(email);
        long codeId = emailCodeService.check(normalizedEmail, EmailCodeService.PURPOSE_RESET, code);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("该邮箱未绑定账号"));
        emailCodeService.consume(codeId);
        userRepository.updatePassword(user.id(), passwordEncoder.encode(newPassword));
    }

    private String validateCredentials(String username, String password) {
        String normalizedUsername = Usernames.normalize(username);
        validatePassword(password);
        return normalizedUsername;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码长度至少 6 位");
        }
    }
}
