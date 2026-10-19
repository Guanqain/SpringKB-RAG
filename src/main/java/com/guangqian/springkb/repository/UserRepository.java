package com.guangqian.springkb.repository;

import com.guangqian.springkb.entity.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓储：基于 JdbcTemplate 的极简 CRUD（避免引入 JPA，保持工程轻量）
 */
@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<User> findByUsername(String username) {
        List<User> users = jdbc.query(
                "SELECT id, username, password_hash, role FROM kb_user WHERE username = ?",
                (rs, rowNum) -> new User(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("role")),
                username);
        return users.stream().findFirst();
    }

    public Optional<User> findById(long id) {
        List<User> users = jdbc.query(
                "SELECT id, username, password_hash, role FROM kb_user WHERE id = ?",
                (rs, rowNum) -> new User(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("role")),
                id);
        return users.stream().findFirst();
    }

    public User insert(String username, String passwordHash, String role) {
        // PostgreSQL 用 RETURNING id 获取自增主键（KeyHolder.getKey() 不支持多列返回键）
        Long id = jdbc.queryForObject(
                "INSERT INTO kb_user (username, password_hash, role) VALUES (?, ?, ?) RETURNING id",
                Long.class, username, passwordHash, role);
        return findById(id)
                .orElseThrow(() -> new IllegalStateException("用户创建失败"));
    }
}
