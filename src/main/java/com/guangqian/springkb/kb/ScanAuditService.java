package com.guangqian.springkb.kb;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 脱敏拦截留痕：只记录「规则类型 + 命中数量」，**绝不记录命中明文或正文**。
 *
 * <p>写入必须用 {@code REQUIRES_NEW}：调用方（发布/编辑）命中后会抛异常并回滚整个事务，
 * 若共用当前事务，审计记录会被一并回滚，等于没留痕。</p>
 */
@Service
public class ScanAuditService {

    private static final Logger log = LoggerFactory.getLogger(ScanAuditService.class);

    /** 审计记录：不包含任何正文或命中值。 */
    public record ScanLog(long id, String username, String action, String docTitle, String ruleType,
                          int hitCount, String createdAt) {
    }

    private static final RowMapper<ScanLog> MAPPER = (rs, rowNum) -> new ScanLog(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("action"),
            rs.getString("doc_title"),
            rs.getString("rule_type"),
            rs.getInt("hit_count"),
            toIso(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbcTemplate;

    public ScanAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String username, String action, String docTitle, SensitiveScanService.Result result) {
        if (result == null || result.findings().isEmpty()) {
            return;
        }
        for (SensitiveScanService.Finding finding : result.findings()) {
            jdbcTemplate.update("""
                    INSERT INTO kb_scan_log (username, action, doc_title, rule_type, hit_count)
                    VALUES (?, ?, ?, ?, ?)
                    """, username, action, docTitle, finding.type(), finding.count());
        }
        log.info("脱敏拦截留痕 username={} action={} rules={}", username, action, result.findings().size());
    }

    public List<ScanLog> recent(int limit) {
        int size = Math.min(500, Math.max(1, limit));
        return jdbcTemplate.query("""
                SELECT id, username, action, doc_title, rule_type, hit_count, created_at
                FROM kb_scan_log
                ORDER BY id DESC
                LIMIT ?
                """, MAPPER, size);
    }

    private static String toIso(Timestamp ts) {
        if (ts == null) {
            return "";
        }
        LocalDateTime ldt = ts.toLocalDateTime();
        return ldt.toLocalDate() + " " + ldt.toLocalTime().withNano(0);
    }
}
