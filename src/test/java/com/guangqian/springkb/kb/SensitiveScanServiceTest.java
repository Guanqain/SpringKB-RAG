package com.guangqian.springkb.kb;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 脱敏扫描规则单测：每类敏感信息都能命中、样例被打码、干净文本不误报。
 */
class SensitiveScanServiceTest {

    private final SensitiveScanService service = new SensitiveScanService();

    @Test
    void detectsEveryRuleType() {
        Map<String, String> samples = new LinkedHashMap<>();
        samples.put("private_key", "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA...");
        samples.put("cloud_key", "access_key = AKIAIOSFODNN7EXAMPLE");
        samples.put("jwt", "Authorization: eyJhbGciOiJIUzI1NiJ9."
                + "eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U");
        samples.put("credential", "数据库密码配置：password = supersecret123");
        samples.put("connection_string", "spring.datasource.url=jdbc:postgresql://localhost:5432/ragdb");
        samples.put("id_card", "身份证号 110101199003078888");
        samples.put("bank_card", "银行卡 6222021234567890123");
        samples.put("phone", "联系电话 13800138000");

        samples.forEach((type, content) -> {
            SensitiveScanService.Result result = service.scan(content);
            assertThat(result.blocked()).as("规则 %s 应命中：%s", type, content).isTrue();
            assertThat(result.findings()).extracting(SensitiveScanService.Finding::type)
                    .as("规则 %s 的命中类型", type)
                    .contains(type);
        });

        // 代码托管令牌属于云密钥规则
        assertThat(service.scan("token: ghp_abcdefghij1234567890abcd").findings())
                .extracting(SensitiveScanService.Finding::type).contains("cloud_key");
        assertThat(service.scan("model.api-key=sk-proj1234567890abcdefghij").findings())
                .extracting(SensitiveScanService.Finding::type).contains("cloud_key");
    }

    @Test
    void masksSamplesAndCapsAtThree() {
        SensitiveScanService.Result result = service.scan(
                "电话 13800138000，备用 13900139000，还有 13700137000 与 13600136000");
        assertThat(result.blocked()).isTrue();
        SensitiveScanService.Finding phone = result.findings().stream()
                .filter(f -> "phone".equals(f.type()))
                .findFirst().orElseThrow();
        assertThat(phone.count()).isEqualTo(4);
        assertThat(phone.samples()).hasSize(3);
        // 打码规则：首 3 位 + **** + 末 2 位
        assertThat(phone.samples().get(0)).isEqualTo("138****00");
        assertThat(phone.samples()).noneMatch(s -> s.contains("13800138000"));
    }

    @Test
    void cleanContentIsNotBlocked() {
        String content = """
                # Spring 事务传播行为

                REQUIRED：加入当前事务；REQUIRES_NEW：挂起当前事务并新建独立事务。
                版本 v2.1，更新时间 2026-10-26，共 3 个片段。
                """;
        SensitiveScanService.Result result = service.scan(content);
        assertThat(result.blocked()).isFalse();
        assertThat(result.findings()).isEmpty();

        // 空内容同样视为干净
        assertThat(service.scan(null).blocked()).isFalse();
        assertThat(service.scan("   ").blocked()).isFalse();
        assertThat(service.scan("").findings()).isEqualTo(List.of());
    }
}
