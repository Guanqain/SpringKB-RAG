package com.guangqian.springkb.setup;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.guangqian.springkb.demo.FakeDocumentSeeder;
import com.guangqian.springkb.service.AppSettingService;
import org.junit.jupiter.api.AfterEach;
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
 * 初始化向导集成测试（springkb_test 真实库）：
 * 无用户时 status=未初始化、init 创建首个 ADMIN 并写入团队配置、重复初始化被拒绝。
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SetupApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    AppSettingService appSettingService;

    @MockitoBean
    EmbeddingModel embeddingModel;

    @MockitoBean
    FakeDocumentSeeder fakeDocumentSeeder;

    @BeforeEach
    void resetUsersAndSettings() {
        // 初始化向导以「库内无用户」为前置条件，每个用例先清空账号与验证码
        jdbcTemplate.update("DELETE FROM kb_user");
        jdbcTemplate.update("DELETE FROM kb_email_code");
        // 初始化占位与团队名按用例重置，避免上次运行残留导致 init 被判定为已初始化
        jdbcTemplate.update("DELETE FROM app_setting WHERE key IN ('team_name', 'setup_complete')");
        // 保持匿名访问开启，避免影响其它测试类对 /api/kb/** 的匿名访问断言
        appSettingService.setAllowAnonymous(true);
    }

    @AfterEach
    void restoreAnonymousSetting() {
        appSettingService.setAllowAnonymous(true);
    }

    // ---------- 1. 未初始化 → init 建 ADMIN → 已初始化 → 重复 init 400 ----------
    @Test
    void initCreatesAdminOnlyWhenSystemHasNoUsers() throws Exception {
        mockMvc.perform(get("/api/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.initialized").value(false))
                .andExpect(jsonPath("$.data.userCount").value(0));

        String email = "admin-" + uid() + "@test.local";
        String code = sendCode(email);

        String adminUsername = "boss" + uid();
        Map<String, Object> init = new HashMap<>();
        init.put("teamName", "测试团队");
        init.put("adminUsername", adminUsername);
        init.put("adminEmail", email);
        init.put("adminPassword", "123456");
        init.put("code", code);
        init.put("allowAnonymous", true);

        MvcResult r = mockMvc.perform(post("/api/setup/init").contentType(APPLICATION_JSON)
                        .content(json(init)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value(adminUsername))
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.role").value("ADMIN"))
                .andReturn();
        String token = readPath(r, "/data/token").asText();

        // 新 ADMIN 的 JWT 直接可访问管理接口
        mockMvc.perform(get("/api/admin/documents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.initialized").value(true))
                .andExpect(jsonPath("$.data.userCount").value(1))
                .andExpect(jsonPath("$.data.teamName").value("测试团队"))
                .andExpect(jsonPath("$.data.allowAnonymous").value(true));

        // 已初始化后再次 init → 400
        mockMvc.perform(post("/api/setup/init").contentType(APPLICATION_JSON)
                        .content(json(init)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ---------- 2. 已存在用户时 status=已初始化、init 直接拒绝 ----------
    @Test
    void initRejectedWhenUsersExist() throws Exception {
        String username = "member" + uid();
        jdbcTemplate.update("""
                INSERT INTO kb_user (username, email, password_hash, role)
                VALUES (?, ?, ?, 'MEMBER')
                """, username, username + "@test.local", passwordEncoder.encode("123456"));

        mockMvc.perform(get("/api/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.initialized").value(true))
                .andExpect(jsonPath("$.data.userCount").value(1));

        mockMvc.perform(post("/api/setup/init").contentType(APPLICATION_JSON)
                        .content(json(Map.of("adminUsername", "other" + uid(), "adminEmail",
                                "other-" + uid() + "@test.local", "adminPassword", "123456", "code", "000000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ---------- 3. 用户名统一 trim、写 setup_complete、重复 init 不再建号 ----------
    @Test
    void initTrimsUsernameWritesSetupCompleteAndRejectsSecondRun() throws Exception {
        String email = "admin-" + uid() + "@test.local";
        String code = sendCode(email);

        Map<String, Object> init = new HashMap<>();
        init.put("teamName", "测试团队");
        init.put("adminUsername", " admin ");
        init.put("adminEmail", email);
        init.put("adminPassword", "123456");
        init.put("code", code);
        init.put("allowAnonymous", true);

        mockMvc.perform(post("/api/setup/init").contentType(APPLICATION_JSON)
                        .content(json(init)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.username").value("admin"));

        assertThat(jdbcTemplate.queryForObject("SELECT username FROM kb_user", String.class))
                .isEqualTo("admin");
        assertThat(userCount()).isEqualTo(1);
        assertThat(setupCompleteCount()).isEqualTo(1);

        // 已初始化后再次 init → 400，且不会多建用户
        mockMvc.perform(post("/api/setup/init").contentType(APPLICATION_JSON)
                        .content(json(init)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("系统已初始化，禁止重复执行"));
        assertThat(userCount()).isEqualTo(1);
    }

    // ---------- 4. 非法用户名（中文 / 空格 / 超长）→ 400 且不创建用户 ----------
    @Test
    void initRejectsInvalidUsernamesWithoutCreatingUser() throws Exception {
        String[] invalidUsernames = {"管理員", "ad min", "a".repeat(33)};
        for (int i = 0; i < invalidUsernames.length; i++) {
            mockMvc.perform(post("/api/setup/init").contentType(APPLICATION_JSON)
                            .content(json(Map.of(
                                    "teamName", "测试团队",
                                    "adminUsername", invalidUsernames[i],
                                    "adminEmail", "invalid-" + i + "-" + uid() + "@test.local",
                                    "adminPassword", "123456",
                                    "code", "000000"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message")
                            .value("用户名只能包含字母、数字、下划线和连字符，长度 3-32 位"));
        }
        assertThat(userCount()).isZero();
        assertThat(setupCompleteCount()).isZero();
    }

    // ---------- helpers ----------

    private long userCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM kb_user", Long.class);
    }

    private long setupCompleteCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app_setting WHERE key = 'setup_complete'", Long.class);
    }

    private String sendCode(String email) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/email-code").contentType(APPLICATION_JSON)
                        .content(json(Map.of("email", email, "purpose", "register"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return readPath(r, "/data/devCode").asText();
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
