package com.guangqian.springkb.repository;

import com.guangqian.springkb.dto.MemberDto;
import com.guangqian.springkb.entity.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 用户仓储：基于 JdbcTemplate 的极简 CRUD（避免引入 JPA，保持工程轻量）。
 */
@Repository
public class UserRepository {

    private static final String USER_COLUMNS = "id, username, email, password_hash, role";

    private static final RowMapper<User> USER_MAPPER = (rs, rowNum) -> new User(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("role"));

    private static final RowMapper<MemberDto> MEMBER_MAPPER = (rs, rowNum) -> new MemberDto(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("email"),
            rs.getString("role"),
            toIso(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<User> findByUsername(String username) {
        return jdbc.query("SELECT " + USER_COLUMNS + " FROM kb_user WHERE username = ?", USER_MAPPER, username)
                .stream().findFirst();
    }

    public Optional<User> findByEmail(String email) {
        return jdbc.query("SELECT " + USER_COLUMNS + " FROM kb_user WHERE lower(email) = lower(?)", USER_MAPPER, email)
                .stream().findFirst();
    }

    public Optional<User> findById(long id) {
        return jdbc.query("SELECT " + USER_COLUMNS + " FROM kb_user WHERE id = ?", USER_MAPPER, id)
                .stream().findFirst();
    }

    /** 全部成员（按创建时间正序，首个即初始化管理员）。 */
    public List<MemberDto> listMembers() {
        return jdbc.query("SELECT id, username, email, role, created_at FROM kb_user ORDER BY id",
                MEMBER_MAPPER);
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM kb_user", Long.class);
        return n == null ? 0L : n;
    }

    public long countByRole(String role) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM kb_user WHERE role = ?", Long.class, role);
        return n == null ? 0L : n;
    }

    public User insert(String username, String email, String passwordHash, String role) {
        // PostgreSQL 用 RETURNING id 获取自增主键（KeyHolder.getKey() 不支持多列返回键）
        Long id = jdbc.queryForObject(
                "INSERT INTO kb_user (username, email, password_hash, role) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, username, email, passwordHash, role);
        return findById(id)
                .orElseThrow(() -> new IllegalStateException("用户创建失败"));
    }

    public void updateRole(long id, String role) {
        jdbc.update("UPDATE kb_user SET role = ?, updated_at = now() WHERE id = ?", role, id);
    }

    public void updatePassword(long id, String passwordHash) {
        jdbc.update("UPDATE kb_user SET password_hash = ?, updated_at = now() WHERE id = ?", passwordHash, id);
    }

    public void updateEmail(long id, String email) {
        jdbc.update("UPDATE kb_user SET email = ?, updated_at = now() WHERE id = ?", email, id);
    }

    private static String toIso(Timestamp ts) {
        if (ts == null) {
            return "";
        }
        LocalDateTime ldt = ts.toLocalDateTime();
        return ldt.toLocalDate() + " " + ldt.toLocalTime().withNano(0);
    }
}
