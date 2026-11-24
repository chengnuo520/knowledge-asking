package com.example.chat.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 会话的 JDBC 实现。
 *
 * <p>同一个用户的会话只保留在多行里、不做单点登录限制：多端同时登录是正常需求，
 * 真要「踢下线」时按 user_id 批量删除即可（{@code deleteByUserExcept}）。
 */
@Repository
public class JdbcSessionStore implements SessionStore {

    private static final RowMapper<SessionRow> MAPPER = (rs, rowNum) -> new SessionRow(
            rs.getString("token_hash"),
            rs.getString("user_id"),
            rs.getTimestamp("last_seen_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcSessionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void create(String tokenHash, String userId, Instant createdAt, Instant expiresAt) {
        jdbc.update("INSERT INTO app_sessions (token_hash, user_id, created_at, last_seen_at, expires_at) "
                        + "VALUES (?, ?::uuid, ?, ?, ?)",
                tokenHash, userId, Timestamp.from(createdAt), Timestamp.from(createdAt),
                Timestamp.from(expiresAt));
    }

    @Override
    public Optional<SessionRow> find(String tokenHash) {
        List<SessionRow> rows = jdbc.query(
                "SELECT token_hash, user_id, last_seen_at, expires_at "
                        + "FROM app_sessions WHERE token_hash = ?", MAPPER, tokenHash);
        return rows.stream().findFirst();
    }

    @Override
    public void touch(String tokenHash, Instant lastSeenAt, Instant expiresAt) {
        jdbc.update("UPDATE app_sessions SET last_seen_at = ?, expires_at = ? WHERE token_hash = ?",
                Timestamp.from(lastSeenAt), Timestamp.from(expiresAt), tokenHash);
    }

    @Override
    public void delete(String tokenHash) {
        jdbc.update("DELETE FROM app_sessions WHERE token_hash = ?", tokenHash);
    }

    @Override
    public int deleteByUserExcept(String userId, String keepTokenHash) {
        return jdbc.update(
                "DELETE FROM app_sessions WHERE user_id = ?::uuid AND token_hash <> ?",
                userId, keepTokenHash);
    }

    @Override
    public int purgeExpired(Instant now) {
        return jdbc.update("DELETE FROM app_sessions WHERE expires_at < ?", Timestamp.from(now));
    }
}
