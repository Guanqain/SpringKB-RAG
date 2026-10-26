package com.guangqian.springkb.me;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import com.guangqian.springkb.common.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 站内通知：目前只有审核结果（通过 / 驳回）。写入与审核状态变更在同一事务内完成，
 * 保证「审核成功必有通知、审核失败不留通知」。
 */
@Service
public class NotificationService {

    public static final String KIND_REVIEW_APPROVED = "review_approved";
    public static final String KIND_REVIEW_REJECTED = "review_rejected";

    public record NotificationItem(long id, String kind, String title, String body, String docId,
                                   boolean read, String createdAt) {
    }

    public record NotificationList(List<NotificationItem> items, int unread) {
    }

    private static final RowMapper<NotificationItem> MAPPER = (rs, rowNum) -> new NotificationItem(
            rs.getLong("id"),
            rs.getString("kind"),
            rs.getString("title"),
            rs.getString("body"),
            rs.getString("doc_id"),
            rs.getBoolean("read"),
            toIso(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbcTemplate;

    public NotificationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 审核结果通知：author 为空（官方/爬虫文档）时静默跳过。 */
    public void notifyReview(String username, String docId, String docTitle, boolean approved, String note) {
        if (username == null || username.isBlank()) {
            return;
        }
        String title = approved ? "文档审核通过：" + docTitle : "文档被驳回：" + docTitle;
        String body = approved
                ? "已进入知识库，团队成员可检索并通过问答引用。"
                : (note == null || note.isBlank() ? "请修改后重新提交。" : note.trim());
        jdbcTemplate.update("""
                INSERT INTO kb_notification (username, kind, title, body, doc_id)
                VALUES (?, ?, ?, ?, ?)
                """, username, approved ? KIND_REVIEW_APPROVED : KIND_REVIEW_REJECTED, title, body, docId);
    }

    public NotificationList list(String username, int limit) {
        int size = Math.min(100, Math.max(1, limit));
        List<NotificationItem> items = jdbcTemplate.query("""
                SELECT id, kind, title, body, doc_id, read, created_at
                FROM kb_notification
                WHERE username = ?
                ORDER BY id DESC
                LIMIT ?
                """, MAPPER, username, size);
        return new NotificationList(items, unreadCount(username));
    }

    public int unreadCount(String username) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM kb_notification WHERE username = ? AND read = false",
                Integer.class, username);
        return n == null ? 0 : n;
    }

    @Transactional
    public void markRead(String username, long id) {
        int updated = jdbcTemplate.update(
                "UPDATE kb_notification SET read = true WHERE id = ? AND username = ?", id, username);
        if (updated == 0) {
            throw new NotFoundException("通知不存在: " + id);
        }
    }

    @Transactional
    public int markAllRead(String username) {
        return jdbcTemplate.update(
                "UPDATE kb_notification SET read = true WHERE username = ? AND read = false", username);
    }

    private static String toIso(Timestamp ts) {
        if (ts == null) {
            return "";
        }
        LocalDateTime ldt = ts.toLocalDateTime();
        return ldt.toLocalDate() + " " + ldt.toLocalTime().withNano(0);
    }
}
