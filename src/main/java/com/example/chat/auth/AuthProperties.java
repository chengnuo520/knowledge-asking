package com.example.chat.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 鉴权配置，对应 application.yml 的 app.auth.*。
 *
 * <p>账号本身全部存在数据库 app_users 表里，这里的配置只影响两件事：
 * 「库里还没有账号时种什么」和「token 怎么校验」。
 */
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** 前端请求头里携带 token 的字段名 */
    private String tokenHeader = "token";

    /** token 有效期（小时） */
    private int sessionHours = 24;

    /** 距上次访问超过多少分钟才刷新一次，避免每个请求都写库 */
    private int idleRenewMinutes = 5;

    /** 是否允许自助注册（默认开放，注册出来的都是 USER） */
    private boolean registrationEnabled = true;

    /** 库里没有管理员时，用这组账号密码种一个 */
    private String adminUsername = "admin";

    private String adminInitPassword = "admin123";

    /** 库里没有普通用户时，是否顺手种一个演示账号（方便验证权限差异） */
    private boolean seedDemoUser = true;

    private String demoUsername = "user";

    private String demoInitPassword = "user12345";

    /** 同一「账号 + IP」连续失败多少次后锁定 */
    private int maxFailedAttempts = 5;

    /** 锁定时长（分钟） */
    private int lockMinutes = 15;

    /** 图片验证码是否开启；关掉只用于本地自动化测试 */
    private boolean captchaEnabled = true;

    /** 验证码有效期（秒） */
    private int captchaTtlSeconds = 120;

    public String getTokenHeader() {
        return tokenHeader;
    }

    public void setTokenHeader(String tokenHeader) {
        this.tokenHeader = tokenHeader;
    }

    public int getSessionHours() {
        return sessionHours;
    }

    public void setSessionHours(int sessionHours) {
        this.sessionHours = sessionHours;
    }

    public int getIdleRenewMinutes() {
        return idleRenewMinutes;
    }

    public void setIdleRenewMinutes(int idleRenewMinutes) {
        this.idleRenewMinutes = idleRenewMinutes;
    }

    public boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    public void setRegistrationEnabled(boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAdminInitPassword() {
        return adminInitPassword;
    }

    public void setAdminInitPassword(String adminInitPassword) {
        this.adminInitPassword = adminInitPassword;
    }

    public boolean isSeedDemoUser() {
        return seedDemoUser;
    }

    public void setSeedDemoUser(boolean seedDemoUser) {
        this.seedDemoUser = seedDemoUser;
    }

    public String getDemoUsername() {
        return demoUsername;
    }

    public void setDemoUsername(String demoUsername) {
        this.demoUsername = demoUsername;
    }

    public String getDemoInitPassword() {
        return demoInitPassword;
    }

    public void setDemoInitPassword(String demoInitPassword) {
        this.demoInitPassword = demoInitPassword;
    }

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public void setMaxFailedAttempts(int maxFailedAttempts) {
        this.maxFailedAttempts = maxFailedAttempts;
    }

    public int getLockMinutes() {
        return lockMinutes;
    }

    public void setLockMinutes(int lockMinutes) {
        this.lockMinutes = lockMinutes;
    }

    public boolean isCaptchaEnabled() {
        return captchaEnabled;
    }

    public void setCaptchaEnabled(boolean captchaEnabled) {
        this.captchaEnabled = captchaEnabled;
    }

    public int getCaptchaTtlSeconds() {
        return captchaTtlSeconds;
    }

    public void setCaptchaTtlSeconds(int captchaTtlSeconds) {
        this.captchaTtlSeconds = captchaTtlSeconds;
    }
}
