package com.example.chat.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 鉴权接口。返回结构对齐参考项目（ai-vue）：
 *
 * <pre>
 * 成功：{"code":"200","msg":"ok","data":{...}}
 * 失败：{"code":"-1","msg":"账号或密码错误"}
 * </pre>
 *
 * <p>token 直接放在 data.token 里，前端存 localStorage，之后每个请求放请求头 {@code token}。
 * 这是参考项目的做法，比 Cookie 更直观，联调时用 curl 也方便。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final AuthProperties props;

    public AuthController(AuthService authService, CaptchaService captchaService, AuthProperties props) {
        this.authService = authService;
        this.captchaService = captchaService;
        this.props = props;
    }

    /**
     * 取一张验证码。返回 captchaId（服务端记账用）和图片的 data URL（直接塞进 img src）。
     *
     * <p>注意：**答案不回传**。图片里只有像素，正确答案只存在服务端内存。
     */
    @GetMapping("/captcha")
    public ResponseEntity<Map<String, Object>> captcha() {
        CaptchaService.CaptchaIssue issue = captchaService.issue();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("captchaId", issue.captchaId());
        data.put("image", issue.imageDataUrl());
        data.put("expireSeconds", issue.expireSeconds());
        return ok(data);
    }

    /** 登录：先验验证码，再查数据库校验密码，都通过才签发 token */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody(required = false) Map<String, String> body,
                                                     HttpServletRequest request) {
        String username = body == null ? null : body.get("username");
        String password = body == null ? null : body.get("password");
        String captchaId = body == null ? null : body.get("captchaId");
        String captchaCode = body == null ? null : body.get("captchaCode");

        // 验证码在密码之前校验：否则攻击者可以拿"密码对不对"当探测信号，验证码就失去意义
        if (props.isCaptchaEnabled() && !captchaService.verify(captchaId, captchaCode)) {
            throw AuthException.badRequest("验证码错误或已过期");
        }

        AuthService.LoginResult result = authService.login(username, password, clientKey(request));
        return ok(Map.of(
                "token", result.rawToken(),
                "userInfo", userInfo(result.user())));
    }

    /** 注册：默认开放，注册出来的一律是 USER 角色 */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody(required = false) Map<String, String> body) {
        String username = body == null ? null : body.get("username");
        String password = body == null ? null : body.get("password");
        String displayName = body == null ? null : body.get("displayName");

        AppUser user = authService.register(username, password, displayName);
        String token = authService.issueSession(user.userId());
        return ok(Map.of("token", token, "userInfo", userInfo(user)));
    }

    /** 当前登录用户；前端启动时可用它校验 token 是否还有效 */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {
        AppUser user = (AppUser) request.getAttribute(AuthFilter.ATTR_USER);
        return ok(Map.of("userInfo", userInfo(user)));
    }

    /** 登出：把这条 token 从 app_sessions 表删掉 */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        authService.logout((String) request.getAttribute(AuthFilter.ATTR_TOKEN));
        return ok(true);
    }

    /** 改密：改完踢掉该账号的其它 token，当前这条保留 */
    @PostMapping("/password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody(required = false) Map<String, String> body,
                                                              HttpServletRequest request) {
        AppUser user = (AppUser) request.getAttribute(AuthFilter.ATTR_USER);
        String oldPassword = body == null ? null : body.get("oldPassword");
        String newPassword = body == null ? null : body.get("newPassword");

        AppUser updated = authService.changePassword(
                user.userId(), oldPassword, newPassword, (String) request.getAttribute(AuthFilter.ATTR_TOKEN));
        return ok(Map.of("userInfo", userInfo(updated)));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, Object>> onAuthError(AuthException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", String.valueOf(e.status().value()));
        body.put("msg", e.getMessage());
        body.put("error", e.getMessage());     // 兼容项目里既有的 {error} 读取方式
        return ResponseEntity.status(e.status()).body(body);
    }

    // ===== 工具 =====

    private static ResponseEntity<Map<String, Object>> ok(Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "200");
        body.put("msg", "ok");
        body.put("data", data);
        return ResponseEntity.ok(body);
    }

    /** userType 沿用参考项目的约定：1 = 普通用户，2 = 管理员 */
    private static Map<String, Object> userInfo(AppUser user) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", user.userId());
        info.put("username", user.username());
        info.put("displayName", user.displayName());
        info.put("role", user.role().name());
        info.put("userType", user.role() == UserRole.ADMIN ? 2 : 1);
        info.put("mustChangePassword", user.mustChangePassword());
        return info;
    }

    /** 登录失败计数的维度之一：客户端 IP */
    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
