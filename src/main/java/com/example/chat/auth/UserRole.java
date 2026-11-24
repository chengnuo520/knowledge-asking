package com.example.chat.auth;

/**
 * 角色。只有两级，刻意不做「权限点」表：
 * 当前需求是「管理员能进后台、普通用户不能」，两个角色就够；
 * 等出现「只读运营」「审核员」这类第三人时，再升级成 role → permissions 的映射。
 */
public enum UserRole {

    /** 管理员：可进入 /admin，可调用 /api/rag/** 全部接口 */
    ADMIN,

    /** 普通用户：只能使用对话 */
    USER;

    /** 入库用 name()，读取时容错（历史脏数据 / 大小写） */
    public static UserRole from(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("角色不能为空");
        }
        try {
            return UserRole.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知角色: " + raw);
        }
    }
}
