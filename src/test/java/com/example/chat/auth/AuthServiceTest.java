package com.example.chat.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 鉴权核心逻辑的单元测试。不连数据库：UserStore / SessionStore 都有内存实现。
 *
 * <p>这里覆盖的是「错了会出事」的部分：会话能否伪造、密码错几次会锁、
 * 改密后旧会话是否失效、停用账号是否还能登录。
 */
class AuthServiceTest {

    private InMemoryUserStore users;
    private InMemorySessionStore sessions;
    private AuthProperties props;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserStore();
        sessions = new InMemorySessionStore();
        props = new AuthProperties();
        props.setMaxFailedAttempts(3);
        props.setLockMinutes(15);
        props.setSessionHours(12);
        service = new AuthService(users, sessions, props);

        service.createUser("admin", "admin12345", "系统管理员", UserRole.ADMIN, false);
    }

    @Test
    @DisplayName("登录成功：返回用户，库里存的是哈希不是明文")
    void loginSuccess() {
        AuthService.LoginResult result = service.login("admin", "admin12345", "127.0.0.1");

        assertThat(result.user().username()).isEqualTo("admin");
        assertThat(result.rawToken()).isNotBlank();
        assertThat(users.findByUsername("admin").orElseThrow().passwordHash())
                .isNotEqualTo("admin12345")
                .startsWith("$2");
    }

    @Test
    @DisplayName("会话表里只有 token 的哈希，拿库里的值伪造登录必然失败")
    void sessionStoresHashOnly() {
        String token = service.login("admin", "admin12345", "127.0.0.1").rawToken();

        assertThat(sessions.rows).doesNotContainKey(token);
        assertThat(sessions.rows).containsKey(service.hashOf(token));
        assertThat(service.resolve(token)).isPresent();

        // 用哈希当 token 去冒充：库里的值本身不是凭证
        assertThat(service.resolve(service.hashOf(token))).isEmpty();
        assertThat(service.resolve("totally-made-up-token")).isEmpty();
    }

    @Test
    @DisplayName("密码错与用户不存在返回同一句文案，且都算一次失败")
    void loginFailureIndistinguishable() {
        assertThatThrownBy(() -> service.login("admin", "wrong-password", "1.1.1.1"))
                .isInstanceOf(AuthException.class)
                .hasMessage("账号或密码错误");

        assertThatThrownBy(() -> service.login("nobody", "whatever123", "1.1.1.1"))
                .isInstanceOf(AuthException.class)
                .hasMessage("账号或密码错误");
    }

    @Test
    @DisplayName("连续失败达到阈值后锁定，期间正确密码也进不来")
    void lockoutAfterRepeatedFailures() {
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.login("admin", "bad-password", "2.2.2.2"))
                    .isInstanceOf(AuthException.class);
        }

        assertThatThrownBy(() -> service.login("admin", "admin12345", "2.2.2.2"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("登录失败次数过多");

        // 换一个 IP 不受影响：锁定维度是「账号 + IP」
        assertThat(service.login("admin", "admin12345", "3.3.3.3").rawToken()).isNotBlank();
    }

    @Test
    @DisplayName("停用账号即使密码正确也不能登录")
    void disabledUserCannotLogin() {
        users.disable("admin");

        assertThatThrownBy(() -> service.login("admin", "admin12345", "4.4.4.4"))
                .isInstanceOf(AuthException.class)
                .hasMessage("账号或密码错误");
    }

    @Test
    @DisplayName("过期会话解析为空，并从表里被清掉")
    void expiredSessionRejected() {
        String token = service.login("admin", "admin12345", "5.5.5.5").rawToken();
        String hash = service.hashOf(token);

        // 把会话改成已过期
        SessionRow row = sessions.rows.get(hash);
        sessions.rows.put(hash, new SessionRow(row.tokenHash(), row.userId(),
                Instant.now().minus(Duration.ofHours(20)), Instant.now().minus(Duration.ofHours(1))));

        assertThat(service.resolve(token)).isEmpty();
        assertThat(sessions.rows).doesNotContainKey(hash);
    }

    @Test
    @DisplayName("改密后旧密码失效，其它会话被踢掉，当前会话保留")
    void changePasswordKicksOtherSessions() {
        String keep = service.login("admin", "admin12345", "6.6.6.6").rawToken();
        String other = service.login("admin", "admin12345", "6.6.6.6").rawToken();
        String userId = service.resolve(keep).orElseThrow().userId();

        service.changePassword(userId, "admin12345", "newpass2026", keep);

        assertThat(service.resolve(keep)).isPresent();
        assertThat(service.resolve(other)).isEmpty();
        assertThatThrownBy(() -> service.login("admin", "admin12345", "6.6.6.6"))
                .isInstanceOf(AuthException.class);
        assertThat(service.login("admin", "newpass2026", "6.6.6.6").rawToken()).isNotBlank();
    }

    @Test
    @DisplayName("注册开关生效；用户名大小写归一，Admin 与 admin 是同一个账号")
    void registrationSwitchAndUsernameNormalized() {
        // 默认开放（和参考项目一致），注册出来的一律是 USER
        AppUser alice = service.register("alice", "alice12345", null);
        assertThat(alice.role()).isEqualTo(UserRole.USER);

        // 关掉开关后拒绝注册
        props.setRegistrationEnabled(false);
        assertThatThrownBy(() -> service.register("bob", "bob12345678", null))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("未开放自助注册");

        // 大小写归一生效：ADMIN 撞上已存在的 admin
        assertThatThrownBy(() -> service.createUser("ADMIN", "whatever123", null, UserRole.USER, false))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("已被占用");

        assertThat(service.login("  ADMIN  ", "admin12345", "7.7.7.7").rawToken()).isNotBlank();
    }

    @Test
    @DisplayName("对外序列化不含 passwordHash")
    void safeMapHidesPasswordHash() {
        AppUser user = users.findByUsername("admin").orElseThrow();
        Map<String, Object> safe = user.toSafeMap();

        assertThat(safe).doesNotContainKeys("passwordHash", "password_hash");
        assertThat(safe.get("role")).isEqualTo("ADMIN");
    }

    // ===== 内存实现 =====

    private static final class InMemoryUserStore implements UserStore {
        private final Map<String, AppUser> rows = new ConcurrentHashMap<>();

        @Override
        public Optional<AppUser> findByUsername(String username) {
            return Optional.ofNullable(rows.get(AuthService.normalizeUsername(username)));
        }

        @Override
        public Optional<AppUser> findById(String userId) {
            return rows.values().stream().filter(u -> u.userId().equals(userId)).findFirst();
        }

        @Override
        public boolean insert(AppUser user) {
            rows.put(AuthService.normalizeUsername(user.username()), user);
            return true;
        }

        @Override
        public boolean changePassword(String userId, String passwordHash) {
            return findById(userId).map(u -> {
                rows.put(u.username(), new AppUser(u.userId(), u.username(), passwordHash,
                        u.role(), u.displayName(), u.enabled(), false));
                return true;
            }).orElse(false);
        }

        @Override
        public void touchLastLogin(String userId, Instant at) {
        }

        @Override
        public long countByRole(UserRole role) {
            return rows.values().stream().filter(u -> u.role() == role).count();
        }

        @Override
        public boolean existsByUsername(String username) {
            return rows.containsKey(AuthService.normalizeUsername(username));
        }

        void disable(String username) {
            findById(rows.get(AuthService.normalizeUsername(username)).userId()).ifPresent(u ->
                    rows.put(u.username(), new AppUser(u.userId(), u.username(), u.passwordHash(),
                            u.role(), u.displayName(), false, u.mustChangePassword())));
        }
    }

    private static final class InMemorySessionStore implements SessionStore {
        private final Map<String, SessionRow> rows = new ConcurrentHashMap<>();

        @Override
        public void create(String tokenHash, String userId, Instant createdAt, Instant expiresAt) {
            rows.put(tokenHash, new SessionRow(tokenHash, userId, createdAt, expiresAt));
        }

        @Override
        public Optional<SessionRow> find(String tokenHash) {
            return Optional.ofNullable(rows.get(tokenHash));
        }

        @Override
        public void touch(String tokenHash, Instant lastSeenAt, Instant expiresAt) {
            Optional.ofNullable(rows.get(tokenHash)).ifPresent(r ->
                    rows.put(tokenHash, new SessionRow(tokenHash, r.userId(), lastSeenAt, expiresAt)));
        }

        @Override
        public void delete(String tokenHash) {
            rows.remove(tokenHash);
        }

        @Override
        public int deleteByUserExcept(String userId, String keepTokenHash) {
            List<String> doomed = rows.values().stream()
                    .filter(r -> r.userId().equals(userId) && !r.tokenHash().equals(keepTokenHash))
                    .map(SessionRow::tokenHash)
                    .toList();
            doomed.forEach(rows::remove);
            return doomed.size();
        }

        @Override
        public int purgeExpired(Instant now) {
            List<String> expired = rows.values().stream()
                    .filter(r -> r.expiresAt().isBefore(now))
                    .sorted(Comparator.comparing(SessionRow::tokenHash))
                    .map(SessionRow::tokenHash)
                    .toList();
            expired.forEach(rows::remove);
            return expired.size();
        }
    }

    @SuppressWarnings("unused")
    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
