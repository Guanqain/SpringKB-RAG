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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证与角色权限集成测试（springkb_test 真实库）：
 * 邮箱验证码回显、注册必须校验验证码、验证码登录、密码可用邮箱登录、MEMBER 访问管理接口 403。
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthApiTest {

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

    @BeforeEach
    void setUp() {
        email = "it-" + uid() + "@test.local";
    }

    // ---------- 1. 完整链路：发码 → 注册 → 密码登录（邮箱）→ 发码登录 ----------
    @Test
    void emailCodeRegisterThenPasswordAndCodeLogin() throws Exception {
        String code = sendCode(email, "register");

        // 错误验证码 → 400（且不消费验证码，后续仍可用正确码注册）
        mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                        .content(json(Map.of("username", "u" + uid(), "email", email,
                                "password", "123456", "code", wrongCode(code)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        String username = "user" + uid();
        MvcResult registered = mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                        .content(json(Map.of("username", username, "email", email,
                                "password", "123456", "code", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value(username))
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andReturn();
        assertThat(readPath(registered, "/data/user/role").asText()).isIn("ADMIN", "MEMBER");

        // 密码登录支持邮箱作为 identifier
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(json(Map.of("username", email, "password", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        // 验证码登录（换一个邮箱账号，避免同邮箱 60 秒发送冷却）
        String loginEmail = "it-" + uid() + "@test.local";
        String loginUser = "user" + uid();
        jdbcTemplate.update("""
                INSERT INTO kb_user (username, email, password_hash, role)
                VALUES (?, ?, ?, 'MEMBER')
                """, loginUser, loginEmail, passwordEncoder.encode("123456"));
        String loginCode = sendCode(loginEmail, "login");
        mockMvc.perform(post("/api/auth/login/code").contentType(APPLICATION_JSON)
                        .content(json(Map.of("email", loginEmail, "code", loginCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    // ---------- 2. 注册校验：缺邮箱 / 缺验证码 / 邮箱重复 ----------
    @Test
    void registerRequiresEmailAndFreshCode() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                        .content(json(Map.of("username", "u" + uid(), "password", "123456",
                                "code", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                        .content(json(Map.of("username", "u" + uid(), "email", email,
                                "password", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        String code = sendCode(email, "register");
        mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                        .content(json(Map.of("username", "user" + uid(), "email", email,
                                "password", "123456", "code", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 同一邮箱再注册（用户名不同、且提供任意 code）→ 邮箱已被注册
        mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                        .content(json(Map.of("username", "user" + uid(), "email", email,
                                "password", "123456", "code", "000000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("邮箱已被注册"));
    }

    // ---------- 3. 角色权限：MEMBER 访问 /api/admin/** 返回 403，ADMIN 正常 ----------
    @Test
    void memberForbiddenFromAdminApis() throws Exception {
        String memberToken = insertUserAndLogin("MEMBER");
        String adminToken = insertUserAndLogin("ADMIN");

        mockMvc.perform(get("/api/admin/documents").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/admin/documents").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ---------- 4. 当前用户：匿名 / 无效 token 返回业务码 401，登录后返回本人 ----------
    @Test
    void meReturns401CodeWhenAnonymousOrTokenInvalid() throws Exception {
        // /api/auth/** 是 permitAll，匿名请求会拿到 AnonymousAuthenticationToken：
        // 必须返回业务码 401，而不是以 anonymousUser 查库报「用户不存在」400
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        String token = insertUserAndLogin("MEMBER");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").isNotEmpty())
                .andExpect(jsonPath("$.data.role").value("MEMBER"));
    }

    // ---------- 5. 验证码登录：邮箱未注册返回 needs_profile，补全资料后直接建号并登录 ----------
    @Test
    void codeLoginForUnregisteredEmailGuidesToCreateAccount() throws Exception {
        String email = "new-" + uid() + "@test.local";
        String code = sendCode(email, "login");

        // 未注册不报错：返回 needs_profile，验证码此时不消费
        mockMvc.perform(post("/api/auth/login/code").contentType(APPLICATION_JSON)
                        .content(json(Map.of("email", email, "code", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.action").value("needs_profile"))
                .andExpect(jsonPath("$.data.email").value(email));

        // 补全用户名 + 密码 → 建号并直接登录
        String username = "new" + uid();
        mockMvc.perform(post("/api/auth/register/email").contentType(APPLICATION_JSON)
                        .content(json(Map.of("email", email, "code", code,
                                "username", username, "password", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value(username))
                .andExpect(jsonPath("$.data.user.email").value(email));

        // 验证码在建号时已消费：同一验证码不能再用
        mockMvc.perform(post("/api/auth/login/code").contentType(APPLICATION_JSON)
                        .content(json(Map.of("email", email, "code", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ---------- helpers ----------

    /** 走 HTTP 发码并取回 devCode（application-test.yml 已开启 code-echo）。 */
    private String sendCode(String email, String purpose) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/email-code").contentType(APPLICATION_JSON)
                        .content(json(Map.of("email", email, "purpose", purpose))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.expiresIn").value(300))
                .andExpect(jsonPath("$.data.devCode").isString())
                .andReturn();
        String code = readPath(r, "/data/devCode").asText();
        assertThat(code).hasSize(6);
        return code;
    }

    private String insertUserAndLogin(String role) throws Exception {
        String username = role.toLowerCase() + uid();
        jdbcTemplate.update("""
                INSERT INTO kb_user (username, email, password_hash, role)
                VALUES (?, ?, ?, ?)
                """, username, username + "@test.local", passwordEncoder.encode("123456"), role);
        MvcResult r = mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return readPath(r, "/data/token").asText();
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

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
