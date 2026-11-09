package com.example.chat.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 限流配置，对应 application.yml 的 rate-limit.*。
 *
 * <p>默认值面向本地开发：每分钟 60 次、按客户端 IP 计数、默认开启。
 * 演示时可临时调小（--rate-limit.limit=2 --rate-limit.window-seconds=60）。
 */
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    /** 是否开启限流 */
    private boolean enabled = true;

    /** 单个窗口内允许的请求数 */
    private int limit = 60;

    /** 窗口长度（秒） */
    private int windowSeconds = 60;

    /** 最多记录多少个客户端，超出后整体重置，防止 IP 无限增长 */
    private int maxClients = 10000;

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getLimit() { return limit; }

    public void setLimit(int limit) { this.limit = limit; }

    public int getWindowSeconds() { return windowSeconds; }

    public void setWindowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; }

    public int getMaxClients() { return maxClients; }

    public void setMaxClients(int maxClients) { this.maxClients = maxClients; }
}
