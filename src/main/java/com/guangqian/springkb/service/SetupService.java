package com.guangqian.springkb.service;

import com.guangqian.springkb.common.Usernames;
import com.guangqian.springkb.dto.AuthResponse;
import com.guangqian.springkb.dto.SetupInitRequest;
import com.guangqian.springkb.dto.SetupStatus;
import com.guangqian.springkb.dto.UserDto;
import com.guangqian.springkb.entity.User;
import com.guangqian.springkb.repository.UserRepository;
import com.guangqian.springkb.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 初始化向导：状态查询与首次初始化。
 *
 * <p>初始化只在系统内没有任何用户时允许执行（防重复初始化 / 防越权创建管理员）；
 * 创建的首个账号固定为 ADMIN，同时写入团队名与匿名访问开关。</p>
 */
@Service
public class SetupService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailCodeService emailCodeService;
    private final AppSettingService appSettingService;

    public SetupService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                        EmailCodeService emailCodeService, AppSettingService appSettingService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.emailCodeService = emailCodeService;
        this.appSettingService = appSettingService;
    }

    public SetupStatus status() {
        long count = userRepository.count();
        return new SetupStatus(
                count > 0,
                count,
                appSettingService.getOrDefault(AppSettingService.KEY_TEAM_NAME, ""),
                appSettingService.allowAnonymous());
    }

    /** 首次初始化：创建 ADMIN 账号并保存团队配置，返回可直接登录的 JWT。 */
    @Transactional
    public AuthResponse init(SetupInitRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (userRepository.count() > 0) {
            throw new IllegalArgumentException("系统已初始化，禁止重复执行");
        }
        String username = Usernames.normalize(req.adminUsername());
        if (req.adminPassword() == null || req.adminPassword().length() < 6) {
            throw new IllegalArgumentException("密码长度至少 6 位");
        }
        String email = EmailCodeService.normalizeEmail(req.adminEmail());
        emailCodeService.verify(email, EmailCodeService.PURPOSE_REGISTER, req.code());

        // 所有校验通过后原子占位：并发请求只有一个能拿到 setup_complete，其余在这里失败并回滚
        if (!appSettingService.claimSetupComplete()) {
            throw new IllegalArgumentException("系统已初始化，禁止重复执行");
        }
        User admin = userRepository.insert(username, email,
                passwordEncoder.encode(req.adminPassword()), AuthService.ROLE_ADMIN);
        appSettingService.put(AppSettingService.KEY_TEAM_NAME,
                req.teamName() == null || req.teamName().isBlank() ? "我的团队" : req.teamName().trim());
        appSettingService.setAllowAnonymous(req.allowAnonymous() == null || req.allowAnonymous());
        return new AuthResponse(jwtUtil.generateToken(admin), UserDto.from(admin));
    }
}
