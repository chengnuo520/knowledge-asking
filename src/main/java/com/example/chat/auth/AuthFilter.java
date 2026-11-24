package com.example.chat.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

/**
 * 身份过滤器：这才是真正的访问边界，前端的路由守卫只是体验。
 *
 * <p>校验顺序：
 * <ol>
 *   <li>不是 /api/ 开头的请求直接放行（静态资源、Vue 页面）</li>
 *   <li>登录、注册接口放行</li>
 *   <li>从请求头 {@code token} 取 token，去 app_sessions 表查，查不到就 401</li>
 *   <li>/api/rag/** 再判一次角色，不是 ADMIN 返回 403</li>
 * </ol>
 *
 * <p>401 的响应体写成 {@code {"code":"-1","msg":"..."}}，和参考项目一致，
 * 前端拦截器只要看到 code=-1 就清掉本地登录态并跳登录页。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)   // 排在 RateLimitFilter(+10) 之后：先限流，再鉴权
public class AuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    /** 控制器通过 request.getAttribute 取当前用户 */
    public static final String ATTR_USER = "authUser";

    /** 控制器改密、登出时要知道「当前这条 token」 */
    public static final String ATTR_TOKEN = "authToken";

    private static final String API_PREFIX = "/api/";

    /** 管理员专属前缀；普通用户访问返回 403 而不是 401 */
    private static final String ADMIN_ONLY_PREFIX = "/api/rag/";

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/captcha");

    private final AuthService authService;
    private final AuthProperties props;

    public AuthFilter(AuthService authService, AuthProperties props) {
        this.authService = authService;
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (!uri.startsWith(API_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        if (PUBLIC_PATHS.contains(uri)) {
            chain.doFilter(request, response);
            return;
        }

        String token = readToken(request);
        Optional<AppUser> user = authService.resolve(token);
        if (user.isEmpty()) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "-1", "登录已过期，请重新登录");
            return;
        }
        if (uri.startsWith(ADMIN_ONLY_PREFIX) && user.get().role() != UserRole.ADMIN) {
            log.warn("越权访问被拒 uri={} user={} role={}", uri, user.get().username(), user.get().role());
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, "403", "需要管理员权限");
            return;
        }

        request.setAttribute(ATTR_USER, user.get());
        request.setAttribute(ATTR_TOKEN, token);
        chain.doFilter(request, response);
    }

    /** 主用请求头 token；同时兼容 Authorization: Bearer，方便 Postman 和自动化测试 */
    private String readToken(HttpServletRequest request) {
        String token = request.getHeader(props.getTokenHeader());
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        return null;
    }

    private static void writeJson(HttpServletResponse response, int status, String code, String msg)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":\"" + code + "\",\"msg\":\"" + msg + "\",\"error\":\"" + msg + "\","
                        + "\"status\":" + status + "}");
    }
}
