package com.example.chat.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 账号的 JDBC 实现。
 *
 * <p>UUID 主键在 SQL 里显式写成 {@code ?::uuid}：JdbcTemplate 会把 String 按 varchar 绑定，
 * 不转型会因为「varchar = uuid」直接报错，而不是隐式转换。
 */
@Repository
public class JdbcUserStore implements UserStore {

    private static final RowMapper<AppUser> MAPPER = (rs, rowNum) -> new AppUser(
            rs.getString("user_id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            UserRole.from(rs.getString("role")),
            rs.getString("display_name"),
            rs.getBoolean("enabled"),
            rs.getBoolean("must_change_password"));

    private static final String COLUMNS =
            "user_id, username, password_hash, role, display_name, enabled, must_change_password";

    private final JdbcTemplate jdbc;

    public JdbcUserStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AppUser> findByUsername(String username) {
        List<AppUser> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM app_users WHERE username = ?", MAPPER, username);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<AppUser> findById(String userId) {
        List<AppUser> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM app_users WHERE user_id = ?::uuid", MAPPER, userId);
        return rows.stream().findFirst();
    }

    @Override
    public boolean insert(AppUser user) {
        int rows = jdbc.update(
                "INSERT INTO app_users (" + COLUMNS + ", created_at) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?)",
                user.userId(), user.username(), user.passwordHash(), user.role().name(),
                user.displayName(), user.enabled(), user.mustChangePassword(), Timestamp.from(Instant.now()));
        return rows == 1;
    }

    @Override
    public boolean changePassword(String userId, String passwordHash) {
        return jdbc.update(
                "UPDATE app_users SET password_hash = ?, must_change_password = FALSE "
                        + "WHERE user_id = ?::uuid", passwordHash, userId) == 1;
    }

    @Override
    public void touchLastLogin(String userId, Instant at) {
        jdbc.update("UPDATE app_users SET last_login_at = ? WHERE user_id = ?::uuid",
                Timestamp.from(at), userId);
    }

    @Override
    public long countByRole(UserRole role) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_users WHERE role = ?", Long.class, role.name());
        return count == null ? 0 : count;
    }

    @Override
    public boolean existsByUsername(String username) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM app_users WHERE username = ?)", Boolean.class, username);
        return Boolean.TRUE.equals(exists);
    }
}
