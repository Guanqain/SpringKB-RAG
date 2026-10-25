package com.guangqian.springkb.me;

import com.guangqian.springkb.common.NotFoundException;
import com.guangqian.springkb.dto.InteractionResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档点赞 / 收藏（登录用户级，计数公开）：
 * 主键 (doc_id, username) 保证幂等，重复点赞/收藏不会产生重复行。
 */
@Service
public class InteractionService {

    private final JdbcTemplate jdbcTemplate;

    public InteractionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public InteractionResult like(String docId, String username) {
        requireDoc(docId);
        jdbcTemplate.update("INSERT INTO kb_doc_like (doc_id, username) VALUES (?, ?) ON CONFLICT DO NOTHING",
                docId, username);
        return state(docId, username);
    }

    @Transactional
    public InteractionResult unlike(String docId, String username) {
        requireDoc(docId);
        jdbcTemplate.update("DELETE FROM kb_doc_like WHERE doc_id = ? AND username = ?", docId, username);
        return state(docId, username);
    }

    @Transactional
    public InteractionResult favorite(String docId, String username) {
        requireDoc(docId);
        jdbcTemplate.update("INSERT INTO kb_doc_favorite (doc_id, username) VALUES (?, ?) ON CONFLICT DO NOTHING",
                docId, username);
        return state(docId, username);
    }

    @Transactional
    public InteractionResult unfavorite(String docId, String username) {
        requireDoc(docId);
        jdbcTemplate.update("DELETE FROM kb_doc_favorite WHERE doc_id = ? AND username = ?", docId, username);
        return state(docId, username);
    }

    public InteractionResult state(String docId, String username) {
        long likeCount = count("SELECT count(*) FROM kb_doc_like WHERE doc_id = ?", docId);
        long favoriteCount = count("SELECT count(*) FROM kb_doc_favorite WHERE doc_id = ?", docId);
        boolean liked = username != null
                && count("SELECT count(*) FROM kb_doc_like WHERE doc_id = ? AND username = ?", docId, username) > 0;
        boolean favorited = username != null
                && count("SELECT count(*) FROM kb_doc_favorite WHERE doc_id = ? AND username = ?", docId, username) > 0;
        return new InteractionResult(likeCount, favoriteCount, liked, favorited);
    }

    private void requireDoc(String docId) {
        if (count("SELECT count(*) FROM kb_document WHERE id = ?", docId) == 0) {
            throw new NotFoundException("文档不存在: " + docId);
        }
    }

    private long count(String sql, Object... args) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class, args);
        return n == null ? 0L : n;
    }
}
