package com.guangqian.springkb.manage;

import java.nio.charset.StandardCharsets;
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

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 系统设置接口集成测试：ADMIN 读写团队名 / 匿名开关，MEMBER 越权 403。
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AdminSettingsApiTest {

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
    void resetSettings() {
        jdbcTemplate.update("DELETE FROM app_setting WHERE key = 'team_name'");
        appSettingService.setAllowAnonymous(true);
    }

    @AfterEach
    void restoreSettings() {
        jdbcTemplate.update("DELETE FROM app_setting WHERE key = 'team_name'");
        appSettingService.setAllowAnonymous(true);
    }

    // ---------- 1. ADMIN GET 返回团队名与匿名开关 ----------
    @Test
    void adminCanReadSettings() throws Exception {
        String token = registerAndLogin("ADMIN");
        mockMvc.perform(get("/api/admin/settings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.teamName").isString())
                .andExpect(jsonPath("$.data.allowAnonymous").isBoolean());
    }

    // ---------- 2. PUT 团队名 trim 后生效，setup/status 读取同一值 ----------
    @Test
    void adminCanUpdateTeamNameAndSetupStatusSeesIt() throws Exception {
        String token = registerAndLogin("ADMIN");
        mockMvc.perform(put("/api/admin/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("teamName", " 平台架构组 ", "allowAnonymous", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.teamName").value("平台架构组"));

        mockMvc.perform(get("/api/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamName").value("平台架构组"));
    }

    // ---------- 3. PUT allowAnonymous=false 立即生效 ----------
    @Test
    void adminCanDisableAnonymousAccess() throws Exception {
        String token = registerAndLogin("ADMIN");
        mockMvc.perform(put("/api/admin/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("teamName", "平台架构组", "allowAnonymous", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.allowAnonymous").value(false));

        mockMvc.perform(get("/api/admin/settings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowAnonymous").value(false));
    }

    // ---------- 4. MEMBER 读写均 403 ----------
    @Test
    void memberForbiddenFromSettings() throws Exception {
        String token = registerAndLogin("MEMBER");
        mockMvc.perform(get("/api/admin/settings").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("teamName", "成员团队", "allowAnonymous", true))))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private String registerAndLogin(String role) throws Exception {
        String username = ("ADMIN".equals(role) ? "admin" : "member") + uid();
        jdbcTemplate.update("""
                INSERT INTO kb_user (username, email, password_hash, role)
                VALUES (?, ?, ?, ?)
                """, username, username + "@test.local", passwordEncoder.encode("123456"), role);
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return jsonPathText(r, "/data/token");
    }

    private String json(Object o) {
        return jsonMapper.writeValueAsString(o);
    }

    private String jsonPathText(MvcResult result, String path) throws Exception {
        JsonNode node = jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode value = node.at(path);
        if (value.isMissingNode() || value.isNull()) {
            throw new IllegalStateException("JSON 路径缺失: " + path + " in " + node);
        }
        return value.asText();
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
