package com.example.chat.auth;

import java.time.Instant;
import java.util.Optional;

/**
 * 账号存储。先定接口再写实现，和 {@code ChunkStore} 同一条理由：
 * 单元测试用内存实现，集成测试用 JDBC，Service 层不用改。
 */
public interface UserStore {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findById(String userId);

    boolean insert(AppUser user);

    /** 改密：同时清掉 must_change_password 标记 */
    boolean changePassword(String userId, String passwordHash);

    void touchLastLogin(String userId, Instant at);

    /** 用于「是否已经有管理员」的启动判断 */
    long countByRole(UserRole role);

    /** 判断用：用户名是否已被占用（注册时先查，避免撞唯一约束报 500） */
    boolean existsByUsername(String username);
}
