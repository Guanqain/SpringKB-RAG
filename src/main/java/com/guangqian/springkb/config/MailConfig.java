package com.guangqian.springkb.config;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * SMTP 邮件发送配置（邮箱验证码投递）。
 *
 * <p>只有 {@code app.mail.enabled=true} 且配置了 host/username/password 才会创建
 * {@link JavaMailSender}；未开启时 {@code EmailCodeService} 自动回落到「只写日志」模式，
 * 本地开发仍可通过 {@code app.auth.code-echo=true} 拿到 devCode。</p>
 *
 * <p>生产通过环境变量注入：MAIL_ENABLED / MAIL_HOST / MAIL_PORT / MAIL_USERNAME /
 * MAIL_PASSWORD / MAIL_FROM / MAIL_SSL，不要把授权码写进配置文件。</p>
 */
@Configuration
public class MailConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
    public JavaMailSender javaMailSender(
            @Value("${app.mail.host:}") String host,
            @Value("${app.mail.port:465}") int port,
            @Value("${app.mail.username:}") String username,
            @Value("${app.mail.password:}") String password,
            @Value("${app.mail.ssl:true}") boolean ssl) {
        if (host.isBlank() || username.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "app.mail.enabled=true 时必须配置 app.mail.host / username / password（可用环境变量 MAIL_HOST 等）");
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        sender.setDefaultEncoding("UTF-8");
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        // 超时必须设：SMTP 不可达时默认无限等待，会把注册/登录请求线程挂死（同类问题见坑 #32）
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");
        if (ssl) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }
        return sender;
    }
}
