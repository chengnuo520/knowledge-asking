package com.example.chat.ratelimit;

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
import java.util.concurrent.ConcurrentHashMap;

/**
 * 极简接口限流：固定窗口（fixed window）+ 按客户端 IP 计数。
 *
 * <p>只拦截 /api/** 的请求，超限返回 429 与 Retry-After 头，前端沿用既有的错误气泡即可展示。
 * 选择固定窗口而不是令牌桶，是因为它只需要「窗口起点 + 计数」两个状态，20 行就能讲清楚；
 * 代价是窗口边界处可能出现最多 2 倍瞬时流量（文档 07 里有讨论）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final String API_PREFIX = "/api/";

    private final RateLimitProperties props;

    /** key = 客户端标识（IP），value = 该客户端当前窗口的状态 */
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (!props.isEnabled() || !request.getRequestURI().startsWith(API_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        long windowMs = Math.max(1, props.getWindowSeconds()) * 1000L;
        String client = clientKey(request);

        // 客户端数量兜底：极端情况下（大量伪造 X-Forwarded-For）整体重置，避免内存无限增长
        if (windows.size() > props.getMaxClients()) {
            windows.clear();
            log.warn("限流窗口表超过 {} 个客户端，已整体重置", props.getMaxClients());
        }

        Window window = windows.computeIfAbsent(client, k -> new Window(System.currentTimeMillis()));

        boolean allowed;
        int remaining;
        long retryAfterSeconds;

        synchronized (window) {
            long now = System.currentTimeMillis();
            if (now - window.start >= windowMs) {   // 窗口过期，重新开始计数
                window.start = now;
                window.count = 0;
            }
            if (window.count < props.getLimit()) {
                window.count++;
                allowed = true;
                remaining = props.getLimit() - window.count;
                retryAfterSeconds = 0;
            } else {
                allowed = false;
                remaining = 0;
                // 向上取整到秒，至少 1 秒，避免 Retry-After: 0 让客户端立刻重试
                retryAfterSeconds = Math.max(1, (window.start + windowMs - now + 999) / 1000);
            }
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(props.getLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));

        if (allowed) {
            chain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        long resetInMs = Math.max(0, window.start + windowMs - now);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setHeader("X-RateLimit-Reset", String.valueOf(retryAfterSeconds));
        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");

        String body = "{\"error\":\"请求过于频繁，请 " + retryAfterSeconds + " 秒后重试\","
                + "\"retryAfterSeconds\":" + retryAfterSeconds + ","
                + "\"limit\":" + props.getLimit() + ","
                + "\"windowSeconds\":" + props.getWindowSeconds() + "}";
        response.getWriter().write(body);

        log.warn("限流拦截 uri={} client={} 窗口剩余 {}ms", request.getRequestURI(), client, resetInMs);
    }

    /** 优先取 X-Forwarded-For 的第一段（经过反向代理时才是真实客户端），否则退回 remoteAddr */
    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    /** 一个客户端在一个窗口内的状态；只在 synchronized(window) 内读写 */
    private static final class Window {
        long start;
        int count;

        Window(long start) {
            this.start = start;
        }
    }
}
