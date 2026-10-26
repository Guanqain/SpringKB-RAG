package com.guangqian.springkb.kb;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.guangqian.springkb.common.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * 脱敏规则注册表：内置规则可开关，自定义规则可增删（如「工号」「内网 IP」「内部域名」）。
 *
 * <p>扫描时以数据库中的**启用规则**为准；内置规则由启动任务幂等播种（已存在则不覆盖，
 * 保证升级时不会把管理员改过的开关重置回默认值）。正则写入前必须能编译，避免坏规则
 * 让整条扫描链路失效。</p>
 */
@Service
public class ScanRuleService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ScanRuleService.class);

    /** 规则视图：builtin=true 的内置规则只允许改标签/开关，不允许改正则或删除。 */
    public record ScanRule(String code, String label, String pattern, boolean builtin, boolean enabled) {
    }

    private static final RowMapper<ScanRule> MAPPER = (rs, rowNum) -> new ScanRule(
            rs.getString("code"),
            rs.getString("label"),
            rs.getString("pattern"),
            rs.getBoolean("builtin"),
            rs.getBoolean("enabled"));

    private final JdbcTemplate jdbcTemplate;
    private final SensitiveScanService sensitiveScanService;

    public ScanRuleService(JdbcTemplate jdbcTemplate, SensitiveScanService sensitiveScanService) {
        this.jdbcTemplate = jdbcTemplate;
        this.sensitiveScanService = sensitiveScanService;
    }

    /** 启动时播种内置规则（幂等，不覆盖已存在的行）。 */
    @Override
    public void run(ApplicationArguments args) {
        seedBuiltinRules();
    }

    public void seedBuiltinRules() {
        for (SensitiveScanService.RuleDef def : SensitiveScanService.builtinRules()) {
            jdbcTemplate.update("""
                    INSERT INTO kb_scan_rule (code, label, pattern, builtin, enabled)
                    VALUES (?, ?, ?, true, true)
                    ON CONFLICT (code) DO NOTHING
                    """, def.code(), def.label(), def.pattern());
        }
        log.info("脱敏规则已就绪，共 {} 条", list().size());
    }

    public List<ScanRule> list() {
        return jdbcTemplate.query("""
                SELECT code, label, pattern, builtin, enabled
                FROM kb_scan_rule
                ORDER BY builtin DESC, created_at, code
                """, MAPPER);
    }

    /** 新增自定义规则：code 自动生成，内置规则不会被覆盖。 */
    public ScanRule create(String label, String pattern) {
        String normalizedLabel = requireText(label, "规则名称必填");
        String normalizedPattern = requirePattern(pattern);
        String code = "custom_" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO kb_scan_rule (code, label, pattern, builtin, enabled)
                VALUES (?, ?, ?, false, true)
                """, code, normalizedLabel, normalizedPattern);
        log.info("新增脱敏规则 code={} label={}", code, normalizedLabel);
        return require(code);
    }

    /** 修改规则：内置规则只允许改标签与开关；自定义规则可改正则。 */
    public ScanRule update(String code, String label, String pattern, Boolean enabled) {
        ScanRule current = require(code);
        String newLabel = label == null || label.isBlank() ? current.label() : label.trim();
        String newPattern = current.pattern();
        if (!current.builtin() && pattern != null && !pattern.isBlank()) {
            newPattern = requirePattern(pattern);
        }
        boolean newEnabled = enabled == null ? current.enabled() : enabled;
        jdbcTemplate.update("""
                UPDATE kb_scan_rule SET label = ?, pattern = ?, enabled = ?, updated_at = now()
                WHERE code = ?
                """, newLabel, newPattern, newEnabled, code);
        return require(code);
    }

    /** 删除自定义规则；内置规则只能关闭。 */
    public void delete(String code) {
        ScanRule current = require(code);
        if (current.builtin()) {
            throw new IllegalArgumentException("内置规则不可删除，可改为关闭");
        }
        jdbcTemplate.update("DELETE FROM kb_scan_rule WHERE code = ?", code);
        log.info("删除脱敏规则 code={}", code);
    }

    /** 按当前启用规则扫描正文。 */
    public SensitiveScanService.Result scan(String content) {
        List<SensitiveScanService.RuleDef> rules = jdbcTemplate.query("""
                SELECT code, label, pattern FROM kb_scan_rule WHERE enabled ORDER BY builtin DESC, code
                """, (rs, rowNum) -> new SensitiveScanService.RuleDef(
                rs.getString("code"), rs.getString("label"), rs.getString("pattern")));
        return sensitiveScanService.scan(content, rules);
    }

    private ScanRule require(String code) {
        List<ScanRule> rows = jdbcTemplate.query("""
                SELECT code, label, pattern, builtin, enabled FROM kb_scan_rule WHERE code = ?
                """, MAPPER, code);
        if (rows.isEmpty()) {
            throw new NotFoundException("规则不存在: " + code);
        }
        return rows.get(0);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String requirePattern(String pattern) {
        String value = requireText(pattern, "正则表达式必填");
        try {
            Pattern.compile(value);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("正则表达式无效: " + e.getDescription());
        }
        return value;
    }
}
