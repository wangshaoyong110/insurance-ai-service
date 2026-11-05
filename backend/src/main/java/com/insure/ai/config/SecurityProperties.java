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

    /**
     * 可信反向代理 IP 列表
     * <p>只有 TCP 对端在此列表中时,才采信 X-Forwarded-For / X-Real-IP;
     * 默认为空表示一律不采信代理头,防止攻击者伪造 IP 绕过限流。
     * 若部署在 Nginx / 网关之后,把网关的内网 IP 填进来,例如 127.0.0.1、10.0.0.8。</p>
     */
    private List<String> trustedProxies = new ArrayList<>();
}
