package com.guangqian.springkb.service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.guangqian.springkb.dto.EmailCodeResult;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 邮箱验证码服务（注册 / 验证码登录 / 绑定邮箱 / 重置密码）。
 *
 * <p>存储：kb_email_code 明文保存 6 位码，5 分钟过期、使用后置 used=true；
 * 同一邮箱 60 秒内只允许发送一次（服务层冷却校验）。</p>
 *
 * <p>投递：{@code app.mail.enabled=true} 时通过 SMTP 真实发送（见 {@code config/MailConfig}）；
 * 未开启时回落到「只写日志」，本地/测试可用 {@code app.auth.code-echo=true} 让接口回显 devCode。
 * SMTP 发送失败会删除刚写入的验证码并返回可读错误，避免冷却期把用户挡在门外。</p>
 */
@Service
public class EmailCodeService {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeService.class);

    public static final String PURPOSE_REGISTER = "register";
    public static final String PURPOSE_LOGIN = "login";
    public static final String PURPOSE_BIND = "bind";
    public static final String PURPOSE_RESET = "reset";

    public static final int TTL_SECONDS = 300;
    public static final int COOLDOWN_SECONDS = 60;

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final SecureRandom random = new SecureRandom();
    private final boolean codeEcho;
    private final String mailFrom;

    public EmailCodeService(JdbcTemplate jdbcTemplate,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.auth.code-echo:false}") boolean codeEcho,
            @Value("${app.mail.from:}") String mailFrom) {
        this.jdbcTemplate = jdbcTemplate;
        this.mailSenderProvider = mailSenderProvider;
        this.codeEcho = codeEcho;
        this.mailFrom = mailFrom;
    }

    /** 发送验证码。purpose 非法 / 邮箱格式错误 / 冷却中均抛 IllegalArgumentException。 */
    public EmailCodeResult send(String email, String purpose) {
        String normalized = normalizeEmail(email);
        validatePurpose(purpose);
        Integer recent = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM kb_email_code
                WHERE lower(email) = lower(?) AND created_at > now() - interval '60 seconds'
                """, Integer.class, normalized);
        if (recent != null && recent > 0) {
            throw new IllegalArgumentException("验证码已发送，请 60 秒后重试");
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO kb_email_code (email, code, purpose, expires_at)
                VALUES (?, ?, ?, now() + interval '5 minutes')
                RETURNING id
                """, Long.class, normalized, code, purpose);
        log.info("邮箱验证码已生成 email={} purpose={} code={}", normalized, purpose, code);
        deliver(normalized, purpose, code, id);
        return new EmailCodeResult(TTL_SECONDS, COOLDOWN_SECONDS, codeEcho ? code : null);
    }

    /** 真实投递验证码邮件；未配置 SMTP 时只记日志（本地开发依赖 code-echo 回显）。 */
    private void deliver(String email, String purpose, String code, Long id) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("未开启邮件投递（app.mail.enabled=false），验证码仅写日志 email={} purpose={}",
                    email, purpose);
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(email);
            helper.setSubject("【SpringKB】" + purposeLabel(purpose) + "验证码");
            helper.setText("您正在进行 SpringKB " + purposeLabel(purpose) + "操作，验证码：" + code
                    + "（5 分钟内有效）。若非本人操作，请忽略本邮件。", false);
            sender.send(message);
            log.info("验证码邮件已发送 email={} purpose={}", email, purpose);
        } catch (Exception e) {
            // 发送失败必须可见：删掉刚写入的码，否则 60 秒冷却会让用户连重试都做不了
            jdbcTemplate.update("DELETE FROM kb_email_code WHERE id = ?", id);
            log.error("验证码邮件发送失败 email={} purpose={}", email, purpose, e);
            throw new IllegalArgumentException("验证码邮件发送失败，请稍后重试");
        }
    }

    private static String purposeLabel(String purpose) {
        return switch (purpose) {
            case PURPOSE_REGISTER -> "注册";
            case PURPOSE_LOGIN -> "登录";
            case PURPOSE_RESET -> "重置密码";
            default -> "绑定邮箱";
        };
    }

    /**
     * 校验验证码但**不消费**，返回验证码记录 id。
     * 用于「邮箱尚未注册」场景：先证明邮箱归属，用户补全信息建号成功后再消费（{@link #consume}）。
     */
    public long check(String email, String purpose, String code) {
        String normalized = normalizeEmail(email);
        validatePurpose(purpose);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("验证码不能为空");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, code FROM kb_email_code
                WHERE lower(email) = lower(?) AND purpose = ? AND used = false AND expires_at > now()
                ORDER BY id DESC LIMIT 1
                """, normalized, purpose);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("验证码不存在或已过期");
        }
        Map<String, Object> row = rows.get(0);
        if (!String.valueOf(row.get("code")).equals(code.trim())) {
            throw new IllegalArgumentException("验证码错误");
        }
        return ((Number) row.get("id")).longValue();
    }

    /** 消费指定验证码（一次性）。 */
    public void consume(long id) {
        jdbcTemplate.update("UPDATE kb_email_code SET used = true WHERE id = ?", id);
    }

    /** 校验并消费验证码；任何不匹配都抛 IllegalArgumentException。 */
    public void verify(String email, String purpose, String code) {
        consume(check(email, purpose, code));
    }

    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        String normalized = email.trim().toLowerCase();
        if (!EMAIL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
        return normalized;
    }

    private static void validatePurpose(String purpose) {
        if (!PURPOSE_REGISTER.equals(purpose) && !PURPOSE_LOGIN.equals(purpose)
                && !PURPOSE_BIND.equals(purpose) && !PURPOSE_RESET.equals(purpose)) {
            throw new IllegalArgumentException("purpose 仅支持 register / login / bind / reset");
        }
    }
}
