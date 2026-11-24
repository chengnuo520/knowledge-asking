package com.example.chat.auth;

/**
 * 账号。passwordHash 只在登录校验与改密时用到，对外序列化时必须剔除——见 {@link #toSafeMap()}。
 */
public record AppUser(
        String userId,
        String username,
        String passwordHash,
        UserRole role,
        String displayName,
        boolean enabled,
        boolean mustChangePassword) {

    /**
     * 对外可见的字段白名单。
     *
     * <p>用白名单而不是「排除 passwordHash」：以后加字段（比如 email）时，
     * 默认不外泄，要外泄得显式加进来。
     */
    public java.util.Map<String, Object> toSafeMap() {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("id", userId);
        body.put("username", username);
        body.put("displayName", displayName);
        body.put("role", role.name());
        body.put("mustChangePassword", mustChangePassword);
        return body;
    }
}
