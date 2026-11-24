package com.example.chat.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Vue3 前端入口：/app 与 /app/ 直接转发到构建产物 index.html。
 *
 * <p>为什么要单独一条：{@code ResourceHandler /app/**} 能处理 /app/admin/knowledge
 * 这类深层路由的回落，但 "/app/" 这种空资源路径不会命中它，会落到 404。
 */
@Controller
public class AppEntryController {

    @GetMapping({"/app", "/app/"})
    public String appEntry() {
        return "forward:/app/index.html";
    }
}
