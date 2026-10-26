package com.guangqian.springkb.kb;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * 文档脱敏扫描：发布「团队可见」文档前检查正文是否混入密钥、私钥、令牌、证件号、
 * 银行卡号、手机号与数据库连接串。
 *
 * <p>策略是**只扫描不修改**：命中即拦截发布并返回命中项（类型 / 数量 / 打码样例），
 * 用户自行删改后重新提交。私有文档不参与扫描与审核，因此可以自由记录敏感内容。</p>
 *
 * <p>样例统一打码后返回，避免扫描结果本身再次泄漏敏感值。</p>
 */
@Service
public class SensitiveScanService {

    /** 单条规则最多保留的样例数（足够用户定位，又不会把正文搬进响应）。 */
    private static final int MAX_SAMPLES = 3;

    /** 命中项：type 为规则标识，label 为中文说明，samples 为打码后的样例。 */
    public record Finding(String type, String label, int count, List<String> samples) {
    }

    /** 扫描结果：blocked=true 表示包含敏感信息，必须修改后才能提交审核。 */
    public record Result(boolean blocked, List<Finding> findings) {

        public static Result clean() {
            return new Result(false, List.of());
        }
    }

    /** 规则定义（code 唯一标识；内置规则的 code 与建档 SQL 一致，便于管理端开关）。 */
    public record RuleDef(String code, String label, String pattern) {
    }

    /**
     * 内置规则：仅在数据库尚无该 code 时由 {@code ScanRuleService} 播种，
     * 运行时以 {@code kb_scan_rule} 的启用状态为准（可关闭，也可加自定义规则）。
     */
    private static final List<RuleDef> BUILTIN = List.of(
            new RuleDef("private_key", "私钥",
                    "-----BEGIN [A-Z ]*PRIVATE KEY-----"),
            new RuleDef("cloud_key", "云访问密钥 / 代码托管令牌",
                    "\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b|\\bghp_[A-Za-z0-9]{20,}\\b|\\bsk-[A-Za-z0-9]{20,}\\b"),
            new RuleDef("jwt", "JWT 令牌",
                    "\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b"),
            new RuleDef("credential", "账号口令 / 密钥字段",
                    "(?i)\\b(?:api[_-]?key|secret[_-]?key|access[_-]?key|client[_-]?secret"
                            + "|auth[_-]?token|access[_-]?token|password|passwd|pwd)\\b\\s*[:=]\\s*[\"']?"
                            + "[A-Za-z0-9_\\-./+=]{6,}"),
            new RuleDef("connection_string", "数据库 / 中间件连接串",
                    "(?i)\\b(?:jdbc|mongodb(?:\\+srv)?|redis|mysql|postgres(?:ql)?|oracle)://"
                            + "[^\\s\"'<>]{6,}"),
            new RuleDef("id_card", "身份证号",
                    "\\b[1-9]\\d{5}(?:19|20)\\d{2}(?:0[1-9]|1[0-2])"
                            + "(?:0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]\\b"),
            new RuleDef("bank_card", "银行卡号",
                    "\\b(?:62\\d{14,17}|\\d{4}[ -]\\d{4}[ -]\\d{4}[ -]\\d{4,7})\\b"),
            new RuleDef("phone", "手机号",
                    "\\b1[3-9]\\d{9}\\b"));

    /** 内置规则定义（供规则表播种使用）。 */
    public static List<RuleDef> builtinRules() {
        return BUILTIN;
    }

    /** 使用内置规则扫描（单元测试与未接入规则表的场景）。 */
    public Result scan(String content) {
        return scan(content, BUILTIN);
    }

    /** 使用指定规则集扫描；正则非法时跳过该条（写入规则前已校验，此处仅防御）。 */
    public Result scan(String content, List<RuleDef> ruleDefs) {
        if (content == null || content.isBlank() || ruleDefs == null || ruleDefs.isEmpty()) {
            return Result.clean();
        }
        List<Finding> findings = new ArrayList<>();
        for (RuleDef def : ruleDefs) {
            Pattern pattern;
            try {
                pattern = Pattern.compile(def.pattern());
            } catch (RuntimeException e) {
                continue;
            }
            Matcher matcher = pattern.matcher(content);
            Set<String> samples = new LinkedHashSet<>();
            int count = 0;
            while (matcher.find() && count < 10_000) {
                count++;
                if (samples.size() < MAX_SAMPLES) {
                    samples.add(mask(matcher.group()));
                }
            }
            if (count > 0) {
                findings.add(new Finding(def.code(), def.label(), count, List.copyOf(samples)));
            }
        }
        return new Result(!findings.isEmpty(), List.copyOf(findings));
    }

    /** 保留首尾少量字符，中间统一打码；短值整体替换为星号。 */
    private static String mask(String raw) {
        String value = raw.replaceAll("\\s+", "");
        if (value.length() <= 6) {
            return "*".repeat(value.length());
        }
        int head = Math.min(4, value.length() / 3);
        return value.substring(0, head) + "****" + value.substring(value.length() - 2);
    }
}
