package com.example.chat.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 初始化账号：库里没有账号时，往 app_users 表里插两条。
 *
 * <p>这里做的事就是一条 INSERT——账号和密码哈希都落在数据库里，
 * 之后登录全部走 {@code SELECT * FROM app_users WHERE username = ?}，
 * 跟配置、环境变量再无关系。要换密码，直接 UPDATE 这张表即可（见文档里给的 SQL）。
 *
 * <p>初始账号（可在 application.yml 的 app.auth 下改）：
 * <ul>
 *   <li>{@code admin / admin123} → ADMIN，能进后台、能改知识库</li>
 *   <li>{@code user / user123}   → USER，只能对话</li>
 * </ul>
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserStore userStore;
    private final AuthService authService;
    private final AuthProperties props;

    public AdminSeeder(UserStore userStore, AuthService authService, AuthProperties props) {
        this.userStore = userStore;
        this.authService = authService;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 整体兜住：种号依赖数据库，数据库不可用时只告警，不能让服务起不来
        try {
            seedAdmin();
            seedDemoUser();
        } catch (RuntimeException e) {
            log.error("账号初始化失败（不影响启动）：{}", e.getMessage());
        }
    }

    private void seedAdmin() {
        // 已经有管理员就什么都不做，绝不覆盖已有密码
        if (userStore.countByRole(UserRole.ADMIN) > 0) {
            return;
        }
        try {
            authService.createUser(props.getAdminUsername(), props.getAdminInitPassword(),
                    "系统管理员", UserRole.ADMIN, false);
            log.info("已创建管理员账号：{} / {}（存于 app_users 表）",
                    AuthService.normalizeUsername(props.getAdminUsername()), props.getAdminInitPassword());
        } catch (RuntimeException e) {
            // 种号失败不阻止启动：数据库暂时不可用时，让服务起来并按请求报错，比直接退出好排查
            log.error("管理员账号创建失败（不影响启动）：{}", e.getMessage());
        }
    }

    private void seedDemoUser() {
        if (!props.isSeedDemoUser()) {
            return;
        }
        if (userStore.existsByUsername(AuthService.normalizeUsername(props.getDemoUsername()))) {
            return;
        }
        try {
            authService.createUser(props.getDemoUsername(), props.getDemoInitPassword(),
                    "演示用户", UserRole.USER, false);
            log.info("已创建普通用户账号：{} / {}（存于 app_users 表）",
                    AuthService.normalizeUsername(props.getDemoUsername()), props.getDemoInitPassword());
        } catch (RuntimeException e) {
            log.error("演示账号创建失败（不影响启动）：{}", e.getMessage());
        }
    }
}
