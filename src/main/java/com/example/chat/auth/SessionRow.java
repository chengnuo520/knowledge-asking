package com.example.chat.auth;

import java.time.Instant;

/**
 * 会话行。不对外暴露，只在 AuthService 内部使用。
 */
public record SessionRow(String tokenHash, String userId, Instant lastSeenAt, Instant expiresAt) {
}
