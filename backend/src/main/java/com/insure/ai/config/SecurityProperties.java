package com.insure.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全相关配置项
 *
 * @author insurance-ai
 */
@Data
@Component
@ConfigurationProperties(prefix = "insure.security")
public class SecurityProperties {

    /** 免认证白名单(相对 context-path) */
    private List<String> whiteList = new ArrayList<>();
}
