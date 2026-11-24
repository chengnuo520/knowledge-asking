package com.example.chat.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 鉴权核心：校验口令、签发会话、解析会话、改密、注册。
 *
 * <p>三条刻意的设计：
 * <ol>
 *   <li><b>不区分「用户不存在」与「密码错」</b>：文案统一为「账号或密码错误」，
 *       否则攻击者可以先用错误的密码探出哪些账号真实存在。</li>
 *   <li><b>用户不存在时也跑一次 BCrypt</b>：否则「不存在」立刻返回、「存在」要算 100ms，
 *       响应时间本身就是账号枚举的信道（timing attack）。</li>
 *   <li><b>库里只存 token 的 SHA-256</b>：排库拿到会话表也无法冒充登录。</li>
 * </ol>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_.-]{3,32}$");
    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX = 64;

    private final UserStore userStore;
    private final SessionStore sessionStore;
    private final AuthProperties props;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 用于拉平「用户不存在」的响应时间，值本身没有意义 */
    private final String dummyHash = encoder.encode("timing-equalizer-not-a-password");

    /** 内存态失败计数：单实例够用，多实例部署要换成 Redis 或库表 */
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public AuthService(UserStore userStore, SessionStore sessionStore, AuthProperties props) {
        this.userStore = userStore;
        this.sessionStore = sessionStore;
        this.props = props;
    }

    /** 登录成功的结果：用户 + 尚未哈希的原始 token（只在此刻存在于内存） */
    public record LoginResult(AppUser user, String rawToken) {
    }

    // ===== 登录 =====

    public LoginResult login(String rawUsername, String rawPassword, String clientKey) {
        String username = normalizeUsername(rawUsername);
        String gateKey = username + "|" + clientKey;

        if (!gateAllows(gateKey)) {
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS,
                    "登录失败次数过多，请 " + props.getLockMinutes() + " 分钟后再试");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw AuthException.unauthorized("账号或密码错误");
        }

        Optional<AppUser> found = userStore.findByUsername(username);
        boolean matched = encoder.matches(rawPassword, found.map(AppUser::passwordHash).orElse(dummyHash));

        if (found.isEmpty() || !matched || !found.get().enabled()) {
            gateFail(gateKey);
            log.warn("登录失败 username={} client={}", username, clientKey);
            // 已停用的账号也给同一句文案，避免泄露「这个账号存在但被禁用」
            throw AuthException.unauthorized("账号或密码错误");
        }

        gateSuccess(gateKey);
        AppUser user = found.get();
        userStore.touchLastLogin(user.userId(), Instant.now());
        sessionStore.purgeExpired(Instant.now());   // 顺手清理，不必上定时任务

        String rawToken = issueSession(user.userId());
        log.info("登录成功 username={} role={}", username, user.role());
        return new LoginResult(user, rawToken);
    }

    /** 签发会话：生成随机 token，库里只落它的 SHA-256 */
    public String issueSession(String userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = B64.encodeToString(bytes);
        Instant now = Instant.now();
        sessionStore.create(sha256(rawToken), userId, now, now.plus(Duration.ofHours(props.getSessionHours())));
        return rawToken;
    }

    /**
     * 解析会话并滑动续期。
     *
     * <p>续期不是每个请求都写库：距上次访问不足 {@code idle-renew-minutes} 就跳过，
     * 否则一个页面几十个请求会把 app_sessions 打成写热点。
     */
    public Optional<AppUser> resolve(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String tokenHash = sha256(rawToken);
        Optional<SessionRow> row = sessionStore.find(tokenHash);
        if (row.isEmpty()) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        if (row.get().expiresAt().isBefore(now)) {
            sessionStore.delete(tokenHash);
            return Optional.empty();
        }
        long idleMinutes = Duration.between(row.get().lastSeenAt(), now).toMinutes();
        if (idleMinutes >= props.getIdleRenewMinutes()) {
            sessionStore.touch(tokenHash, now, now.plus(Duration.ofHours(props.getSessionHours())));
        }
        return userStore.findById(row.get().userId()).filter(AppUser::enabled);
    }

    public String hashOf(String rawToken) {
        return rawToken == null ? null : sha256(rawToken);
    }

    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            sessionStore.delete(sha256(rawToken));
        }
    }

    // ===== 改密 =====

    public AppUser changePassword(String userId, String oldPassword, String newPassword, String currentToken) {
        AppUser user = userStore.findById(userId)
                .orElseThrow(() -> AuthException.unauthorized("登录状态已失效，请重新登录"));

        if (oldPassword == null || !encoder.matches(oldPassword, user.passwordHash())) {
            throw AuthException.badRequest("原密码不正确");
        }
        validatePassword(newPassword);
        if (encoder.matches(newPassword, user.passwordHash())) {
            throw AuthException.badRequest("新密码不能与原密码相同");
        }

        userStore.changePassword(userId, encoder.encode(newPassword));
        // 改密后踢掉其它会话：密码泄露过的人，最希望的就是「把别人踢下线」
        if (currentToken != null) {
            sessionStore.deleteByUserExcept(userId, sha256(currentToken));
        }
        log.info("修改密码成功 userId={}", userId);
        return userStore.findById(userId).orElse(user);
    }

    // ===== 注册 / 建号 =====

    public AppUser register(String rawUsername, String password, String displayName) {
        if (!props.isRegistrationEnabled()) {
            throw AuthException.forbidden("未开放自助注册，请联系管理员开通账号");
        }
        return createUser(rawUsername, password, displayName, UserRole.USER, false);
    }

    public AppUser createUser(String rawUsername, String password, String displayName,
                              UserRole role, boolean mustChangePassword) {
        String username = normalizeUsername(rawUsername);
        validateUsername(username);
        validatePassword(password);

        if (userStore.existsByUsername(username)) {
            throw AuthException.badRequest("用户名已被占用");
        }
        AppUser user = new AppUser(
                UUID.randomUUID().toString(),
                username,
                encoder.encode(password),
                role,
                (displayName == null || displayName.isBlank()) ? username : displayName.trim(),
                true,
                mustChangePassword);
        if (!userStore.insert(user)) {
            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR, "创建账号失败");
        }
        return user;
    }

    // ===== 校验 =====

    public void validateUsername(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw AuthException.badRequest("用户名需为 3–32 位小写字母、数字、下划线、点或短横线");
        }
    }

    public void validatePassword(String password) {
        if (password == null || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw AuthException.badRequest("密码长度需在 " + PASSWORD_MIN + "–" + PASSWORD_MAX + " 位之间");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw AuthException.badRequest("密码需同时包含字母与数字");
        }
    }

    /**
     * 用户名统一小写：否则 Admin 与 admin 会成为两个账号，
     * 而用户以为是同一个，登录失败时完全无从排查。
     */
    public static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    // ===== 失败计数 =====

    private boolean gateAllows(String key) {
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return true;
        }
        synchronized (attempt) {
            if (attempt.lockedUntil != null) {
                if (Instant.now().isBefore(attempt.lockedUntil)) {
                    return false;
                }
                attempt.lockedUntil = null;
                attempt.fails = 0;
            }
            return true;
        }
    }

    private void gateFail(String key) {
        if (attempts.size() > 10000) {     // 兜底：避免被随机用户名打爆内存
            attempts.clear();
        }
        Attempt attempt = attempts.computeIfAbsent(key, k -> new Attempt());
        synchronized (attempt) {
            attempt.fails++;
            if (attempt.fails >= props.getMaxFailedAttempts()) {
                attempt.lockedUntil = Instant.now().plus(Duration.ofMinutes(props.getLockMinutes()));
                attempt.fails = 0;
                log.warn("登录锁定 key={} 时长={}分钟", key, props.getLockMinutes());
            }
        }
    }

    private void gateSuccess(String key) {
        attempts.remove(key);
    }

    private static final class Attempt {
        int fails;
        Instant lockedUntil;
    }

    // ===== 工具 =====

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }
}
