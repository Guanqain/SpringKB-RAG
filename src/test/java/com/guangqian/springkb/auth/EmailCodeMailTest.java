package com.guangqian.springkb.auth;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import com.guangqian.springkb.demo.FakeDocumentSeeder;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 邮箱验证码「真实投递」测试：开启 app.mail.enabled 后必须走 JavaMailSender 发信；
 * 发送失败要可见（返回可读错误 + 删除刚写入的码，避免 60 秒冷却把用户挡在门外）。
 *
 * <p>用独立 properties 启动新上下文（MailConfig 才会创建 JavaMailSender），并用 @MockitoBean 替换为 mock，
 * 保证不连接任何真实 SMTP。</p>
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "app.mail.enabled=true",
        "app.mail.host=smtp.test.local",
        "app.mail.username=no-reply@test.local",
        "app.mail.password=test-password",
        "app.mail.from=no-reply@test.local"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class EmailCodeMailTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    JsonMapper jsonMapper;

    @MockitoBean
    EmbeddingModel embeddingModel;

    @MockitoBean
    FakeDocumentSeeder fakeDocumentSeeder;

    @MockitoBean
    JavaMailSender javaMailSender;

    @Test
    void codeIsDeliveredByMailWhenSmtpEnabled() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        given(javaMailSender.createMimeMessage()).willReturn(message);

        String email = "mail-" + uid() + "@test.local";
        MvcResult r = mockMvc.perform(post("/api/auth/email-code").contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("email", email, "purpose", "register"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String code = jsonMapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .at("/data/devCode").asText();
        assertThat(code).hasSize(6);

        verify(javaMailSender).send(message);
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo(email);
        assertThat(message.getSubject()).contains("注册验证码");
        assertThat(String.valueOf(message.getContent())).contains(code);
    }

    @Test
    void deliveryFailureRemovesCodeAndReturnsReadableError() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        given(javaMailSender.createMimeMessage()).willReturn(message);
        willThrow(new MailSendException("smtp down")).given(javaMailSender).send(any(MimeMessage.class));

        String email = "mail-" + uid() + "@test.local";
        mockMvc.perform(post("/api/auth/email-code").contentType(APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("email", email, "purpose", "register"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("验证码邮件发送失败，请稍后重试"));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM kb_email_code WHERE lower(email) = lower(?)", Integer.class, email);
        assertThat(rows).isZero();
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
