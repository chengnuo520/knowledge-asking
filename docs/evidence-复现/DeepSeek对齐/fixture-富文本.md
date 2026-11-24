# 接口限流方案说明

限流是保护后端最省事的一步：**不用改业务代码**，只要在过滤器里拦一道。

## 一、为什么选固定窗口

常见算法有三种，取舍如下：

- **固定窗口**：实现最简单，窗口边界会有 2 倍突刺
- **滑动窗口**：平滑一些，但要记录更多状态
- **令牌桶**：能应对突发流量，实现成本最高

> 演示场景用固定窗口足够：单机、单实例、QPS 不高。

## 二、核心代码

关键是把「判过期 + 自增 + 算剩余」做成原子操作：

```java
public boolean tryAcquire(String client) {
    Window w = clients.computeIfAbsent(client, k -> new Window());
    synchronized (w) {
        long now = System.currentTimeMillis();
        if (now - w.start >= windowMillis) {
            w.start = now;
            w.count = 0;
        }
        if (w.count >= limit) {
            return false;
        }
        w.count++;
        return true;
    }
}
```

前端不需要任何改动，因为 429 的响应体里已经带了 `error` 字段：

```json
{ "error": "请求过于频繁，请 59 秒后重试" }
```

## 三、参数对照

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `rate-limit.enabled` | `true` | 总开关，压测时可关 |
| `rate-limit.limit` | `60` | 每个窗口允许的请求数 |
| `rate-limit.window-seconds` | `60` | 窗口长度（秒） |

## 四、验证方式

一次完整的验证只需要三步：

1. 启动应用，把 `limit` 调成 `3`
2. 连续请求 4 次 `/api/models`
3. 观察第 4 次返回 `429` 和 `Retry-After`

参考资料：[Spring 官方文档](https://docs.spring.io/spring-framework/reference/web/webmvc/filters.html)、[RFC 6585](https://www.rfc-editor.org/rfc/rfc6585#section-4)。

---

*补充：`X-Forwarded-For` 只在网关可信时使用*，否则客户端可以伪造 IP 绕过限流。
