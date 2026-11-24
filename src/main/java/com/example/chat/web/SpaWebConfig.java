package com.example.chat.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Vue3 前端（构建产物在 classpath:/static/app/）的 history 模式兜底。
 *
 * <p>Vite 用 {@code createWebHistory('/app/')}，用户刷新 /app/admin/knowledge 时
 * 服务端并不存在这个文件，默认会 404。这里把「找不到的静态资源」回落到 app/index.html，
 * 交给前端路由处理；真实存在的 js/css（带扩展名）仍按原样返回。
 *
 * <p>旧版单页仍在 {@code /} 下，两者互不影响。
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    private static final String APP_ROOT = "classpath:/static/app/";

    private static final String APP_INDEX = "/static/app/index.html";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/app/**")
                .addResourceLocations(APP_ROOT)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        if (resourcePath != null && !resourcePath.isBlank()) {
                            Resource requested = location.createRelative(resourcePath);
                            if (requested.exists() && requested.isReadable()) {
                                return requested;
                            }
                        }
                        return new ClassPathResource(APP_INDEX);
                    }
                });
    }
}
