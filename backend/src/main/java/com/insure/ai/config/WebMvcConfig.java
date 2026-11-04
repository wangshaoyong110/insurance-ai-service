package com.insure.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC 配置:静态资源映射(上传文件访问) + 跨域
 *
 * @author insurance-ai
 */
@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 上传文件存储根目录 */
    @Value("${insure.upload.path}")
    private String uploadPath;

    /**
     * 将本地磁盘目录映射为 /upload/** 访问路径
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path absolutePath = Paths.get(uploadPath).toAbsolutePath().normalize();
        String location = "file:" + absolutePath + File.separator;
        log.info("[静态资源] /upload/** -> {}", location);
        registry.addResourceHandler("/upload/**").addResourceLocations(location);
    }

    /**
     * 跨域配置(前后端分离部署时使用,生产环境建议收紧 allowedOriginPatterns)
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
