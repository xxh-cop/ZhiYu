package com.zhiyu.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web MVC 配置（批次 2）。
 *
 * <p>将本地文件存储目录映射到 URL 路径 {@code /uploads/**}，
 * 使上传的图片和视频可以通过 HTTP 直接访问。
 *
 * <p>映射关系：{@code /uploads/**} → {@code file:./uploads/}
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${zhiyu.file.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /uploads/** 映射到本地 ./uploads/ 目录
        String location = Paths.get(uploadDir).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}
