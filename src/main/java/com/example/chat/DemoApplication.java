package com.example.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Spring AI 自动配置在 application.yml 里统一 exclude 掉了
// 我们自己在 ChatModelFactory 里手动创建模型实例
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
