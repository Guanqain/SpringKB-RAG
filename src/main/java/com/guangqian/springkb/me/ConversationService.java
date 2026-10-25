package com.guangqian.springkb.me;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import com.guangqian.springkb.common.NotFoundException;
import com.guangqian.springkb.dto.AppendMessageRequest;
import com.guangqian.springkb.dto.ConversationDetail;
import com.guangqian.springkb.dto.ConversationMessage;
import com.guangqian.springkb.dto.ConversationSummary;
import com.guangqian.springkb.rag.SourceRef;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 问答历史会话（PostgreSQL 持久化）：
 * <ul>
 *   <li>按登录用户名隔离，用户只能读写自己的会话；</li>
 *   <li>上下文窗口：每个会话只保留最近 {@code app.conversation.window-size} 轮问答，超出即删最旧的；</li>
 *   <li>生存周期：{@code app.conversation.ttl-days} 天无新消息的会话在列表/新建时顺带清理，避免僵尸会话。</li>
 * </ul>
 */
@Service
public class ConversationService {

    private static final TypeReference<List<SourceRef>> SOURCE_LIST = new TypeReference<>() {
    };

    private static final int MAX_TITLE_LENGTH = 60;

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;
    private final int windowSize;
    private final int ttlDays;

    public ConversationService(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper,
            @Value("${app.conversation.window-size:10}") int windowSize,
            @Value("${app.conversation.ttl-days:30}") int ttlDays) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
        this.windowSize = Math.max(1, windowSize);
        this.ttlDays = Math.max(1, ttlDays);
    }

    /** 会话列表（最近更新在前）；顺带清理过期会话。 */
    public List<ConversationSummary> list(String username) {
        sweepExpired(username);
        return jdbcTemplate.query("""
                SELECT c.id, c.title, c.updated_at, count(m.id) AS message_count
                FROM kb_conversation c
                LEFT JOIN kb_message m ON m.conversation_id = c.id
                WHERE c.username = ?
                GROUP BY c.id, c.title, c.updated_at
                ORDER BY c.updated_at DESC, c.id DESC
                """, (rs, rowNum) -> new ConversationSummary(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getInt("message_count"),
                toIso(rs.getTimestamp("updated_at"))), username);
    }

    @Transactional
    public ConversationDetail create(String username, String title) {
        sweepExpired(username);
        String normalized = normalizeTitle(title);
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO kb_conversation (username, title) VALUES (?, ?) RETURNING id",
                Long.class, username, normalized);
        return detail(username, id);
    }

    public ConversationDetail detail(String username, long id) {
        String title = jdbcTemplate.query(
                "SELECT title FROM kb_conversation WHERE id = ? AND username = ?",
                (rs, rowNum) -> rs.getString("title"), id, username).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("会话不存在: " + id));
        Timestamp updatedAt = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM kb_conversation WHERE id = ?", Timestamp.class, id);
        List<ConversationMessage> messages = jdbcTemplate.query(
                "SELECT id, question, answer, found, version, sources::text AS sources, created_at "
                        + "FROM kb_message WHERE conversation_id = ? ORDER BY id",
                (rs, rowNum) -> new ConversationMessage(
                        rs.getLong("id"),
                        rs.getString("question"),
                        rs.getString("answer"),
                        rs.getBoolean("found"),
                        rs.getString("version"),
                        parseSources(rs.getString("sources")),
                        toIso(rs.getTimestamp("created_at"))), id);
        return new ConversationDetail(id, title, toIso(updatedAt), messages);
    }

    @Transactional
    public ConversationDetail rename(String username, long id, String title) {
        int n = jdbcTemplate.update("UPDATE kb_conversation SET title = ?, updated_at = now() WHERE id = ? AND username = ?",
                normalizeTitle(title), id, username);
        if (n == 0) {
            throw new NotFoundException("会话不存在: " + id);
        }
        return detail(username, id);
    }

    @Transactional
    public void delete(String username, long id) {
        int n = jdbcTemplate.update("DELETE FROM kb_conversation WHERE id = ? AND username = ?", id, username);
        if (n == 0) {
            throw new NotFoundException("会话不存在: " + id);
        }
    }

    /** 追加一轮问答：写入后把超出窗口大小的旧消息裁掉，并刷新会话 updated_at。 */
    @Transactional
    public ConversationDetail append(String username, long id, AppendMessageRequest req) {
        requireOwned(username, id);
        if (req.question() == null || req.question().isBlank()) {
            throw new IllegalArgumentException("question 必填");
        }
        if (req.answer() == null || req.answer().isBlank()) {
            throw new IllegalArgumentException("answer 必填");
        }
        String sourcesJson = jsonMapper.writeValueAsString(req.sources() == null ? List.of() : req.sources());
        boolean found = req.found() == null || req.found();
        String version = req.version() == null || req.version().isBlank() ? null : req.version();
        jdbcTemplate.update("""
                INSERT INTO kb_message (conversation_id, question, answer, found, version, sources)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                """, id, req.question(), req.answer(), found, version, sourcesJson);
        // 上下文窗口：只保留最近 windowSize 条（按自增 id 排序）
        jdbcTemplate.update("""
                DELETE FROM kb_message
                WHERE conversation_id = ?
                  AND id NOT IN (
                      SELECT id FROM kb_message WHERE conversation_id = ? ORDER BY id DESC LIMIT ?
                  )
                """, id, id, windowSize);
        jdbcTemplate.update("UPDATE kb_conversation SET updated_at = now() WHERE id = ? AND username = ?",
                id, username);
        return detail(username, id);
    }

    /** 清理该用户超过 TTL 天未更新的会话（级联删除消息）。 */
    private void sweepExpired(String username) {
        jdbcTemplate.update("""
                DELETE FROM kb_conversation
                WHERE username = ? AND updated_at < now() - make_interval(days => ?)
                """, username, ttlDays);
    }

    private void requireOwned(String username, long id) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM kb_conversation WHERE id = ? AND username = ?", Long.class, id, username);
        if (n == null || n == 0) {
            throw new NotFoundException("会话不存在: " + id);
        }
    }

    private List<SourceRef> parseSources(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return jsonMapper.readValue(json, SOURCE_LIST);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String normalizeTitle(String title) {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) {
            t = "新会话";
        }
        return t.length() <= MAX_TITLE_LENGTH ? t : t.substring(0, MAX_TITLE_LENGTH);
    }

    private static String toIso(Timestamp ts) {
        if (ts == null) {
            return "";
        }
        LocalDateTime ldt = ts.toLocalDateTime();
        return ldt.toLocalDate() + " " + ldt.toLocalTime().withNano(0);
    }
}
