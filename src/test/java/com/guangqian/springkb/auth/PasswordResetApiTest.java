package com.guangqian.springkb.auth;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import com.guangqian.springkb.demo.FakeDocumentSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 忘记密码 → 邮箱验证码 → 重设密码集成测试（springkb_test 真实库）：
 * 错误码不消费、新密码生效旧密码失效、验证码一次性、未注册邮箱返回一致响应（防账号探测）。
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PasswordResetApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    EmbeddingModel embeddingModel;

    @MockitoBean
    FakeDocumentSeeder fakeDocumentSeeder;

    private String email;
    private String username;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        username = "reset" + uid;
        email = username + "@test.local";
        jdbcTemplate.update("""
                INSERT INTO kb_user (username, email, password_hash, role)
                VALUES (?, ?, ?, 'MEMBER')
                """, username, email, passwordEncoder.encode("123456"));
    }

    // ---------- 1. 完整链路：发码 → 错码失败 → 正确码重置 → 新密码登录 ----------
    @Test
    void resetPasswordWithEmailCodeThenLoginWithNewPassword() throws Exception {
        String code = requestResetCode(email);

        // 新密码过短 → 400，且验证码未被消费
        mockMvc.perform(post("/api/auth/password/reset").contentType(APPLICATION_JSON)
                        .content(json(Map.of("email", email, "code", code, "newPassword", "123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("密码长度至少 6 位"));

        // 错误验证码 → 400（不消费，正确码随后仍可用）
        mockMvc.perform(post("/api/auth/password/reset").contentType(APPLICATION_JSON)
                        .content(json(Map.of("email", email, "code", wrongCode(code), "newPassword", "654321"))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("验证码错误"));

        mockMvc.perform(post("/api/auth/password/reset").contentType(APPLICATION_JSON)
                        .content(json(Map.of("email", email, "code", code, "newPassword", "654321"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 旧密码失效、新密码可登录
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(json(Map.of("username", email, "password", "123456"))))
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(json(Map.of("username", email, "password", "654321"))))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        // 验证码一次性：同一码不能再次重置
        mockMvc.perform(post("/api/auth/password/reset").contentType(APPLICATION_JSON)
                        .content(json(Map.of("email", email, "code", code, "newPassword", "abcdef"))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("验证码不存在或已过期"));

        // 新密码仍为上一次重置的结果
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(json(Map.of("username", email, "password", "654321"))))
                .andExpect(jsonPath("$.code").value(0));
    }

    // ---------- 2. 未注册邮箱：返回一致响应，不暴露账号是否存在 ----------
    @Test
    void unregisteredEmailGetsSameResponseWithoutSendingCode() throws Exception {
        String unknown = "nobody-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        MvcResult r = mockMvc.perform(post("/api/auth/password/code").contentType(APPLICATION_JSON)
                        .content(json(Map.of("email", unknown))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.expiresIn").value(300))
                .andExpect(jsonPath("$.data.cooldown").value(60))
                .andReturn();
        JsonNode node = jsonMapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        // 未注册邮箱不生成验证码（devCode 为 null），响应结构与已注册邮箱一致
        assertThat(node.path("data").path("devCode").isNull()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM kb_email_code WHERE lower(email) = lower(?)",
                Integer.class, unknown)).isZero();

        // 用任意验证码重置未注册邮箱 → 验证码不存在/已过期（不泄漏账号是否存在）
        mockMvc.perform(post("/api/auth/password/reset").contentType(APPLICATION_JSON)
                        .content(json(Map.of("email", unknown, "code", "000000", "newPassword", "654321"))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("验证码不存在或已过期"));
    }

    // ---------- helpers ----------

    /** 走 HTTP 发重置验证码并取回 devCode（application-test.yml 已开启 code-echo）。 */
    private String requestResetCode(String targetEmail) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/password/code").contentType(APPLICATION_JSON)
                        .content(json(Map.of("email", targetEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.expiresIn").value(300))
                .andExpect(jsonPath("$.data.devCode").isString())
                .andReturn();
        String code = readPath(r, "/data/devCode").asText();
        assertThat(code).hasSize(6);
        return code;
    }

    private static String wrongCode(String code) {
        return "000000".equals(code) ? "111111" : "000000";
    }

    private String json(Object o) {
        return jsonMapper.writeValueAsString(o);
    }

    private JsonNode readPath(MvcResult result, String path) throws Exception {
        JsonNode node = jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode v = node.at(path);
        if (v.isMissingNode() || v.isNull()) {
            throw new IllegalStateException("JSON 路径缺失: " + path + " in " + node);
        }
        return v;
    }
}
