package com.example.chat.auth;

import java.time.Instant;
import java.util.Optional;

/**
 * 会话存储。key 恒为 token 的 SHA-256，明文 token 只存在于浏览器 Cookie 与内存中。
 */
public interface SessionStore {

    void create(String tokenHash, String userId, Instant createdAt, Instant expiresAt);

    Optional<SessionRow> find(String tokenHash);

    /** 滑动续期：更新 last_seen_at 与 expires_at */
    void touch(String tokenHash, Instant lastSeenAt, Instant expiresAt);

    void delete(String tokenHash);

    /** 改密后踢掉该用户其它会话，保留当前这条 */
    int deleteByUserExcept(String userId, String keepTokenHash);

    /** 清理过期会话，在登录时顺手调用即可，不必上定时任务 */
    int purgeExpired(Instant now);
}
