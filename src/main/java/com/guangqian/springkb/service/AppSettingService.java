package com.guangqian.springkb.service;

import java.util.Optional;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 应用级键值配置（app_setting 表）：初始化向导写入团队名与匿名访问开关。
 *
 * <p>匿名访问开关被 SecurityConfig 的授权规则直接读取，因此缓存在内存中
 * （volatile），启动时加载、写入时同步刷新，避免每个请求查库。</p>
 */
@Service
public class AppSettingService {

    private static final Logger log = LoggerFactory.getLogger(AppSettingService.class);

    public static final String KEY_TEAM_NAME = "team_name";
    public static final String KEY_ALLOW_ANONYMOUS = "allow_anonymous";
    public static final String KEY_SETUP_COMPLETE = "setup_complete";

    private final JdbcTemplate jdbcTemplate;

    /** 默认允许匿名（与历史行为一致：未初始化时也能直接浏览问答与知识库）。 */
    private volatile boolean allowAnonymous = true;

    public AppSettingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void load() {
        try {
            get(KEY_ALLOW_ANONYMOUS).ifPresent(v -> allowAnonymous = Boolean.parseBoolean(v));
        } catch (Exception e) {
            log.warn("读取 app_setting 失败，保持默认匿名访问开启：{}", e.getMessage());
        }
    }

    public Optional<String> get(String key) {
        return jdbcTemplate.query("SELECT value FROM app_setting WHERE key = ?",
                        (rs, rowNum) -> rs.getString("value"), key)
                .stream().findFirst();
    }

    public String getOrDefault(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    public void put(String key, String value) {
        jdbcTemplate.update("""
                INSERT INTO app_setting (key, value, updated_at) VALUES (?, ?, now())
                ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()
                """, key, value);
        if (KEY_ALLOW_ANONYMOUS.equals(key)) {
            allowAnonymous = Boolean.parseBoolean(value);
        }
    }

    public boolean allowAnonymous() {
        return allowAnonymous;
    }

    public void setAllowAnonymous(boolean allow) {
        put(KEY_ALLOW_ANONYMOUS, Boolean.toString(allow));
    }
}
