package com.insure.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 大模型配置项
 *
 * @author insurance-ai
 */
@Data
@Component
@ConfigurationProperties(prefix = "insure.deepseek")
public class DeepSeekProperties {

    /** 接口地址,如 https://api.deepseek.com */
    private String baseUrl;

    /** API Key */
    private String apiKey;

    /** 模型名称,如 deepseek-chat */
    private String model;

    /** 读取超时(毫秒) */
    private Integer timeout = 60000;

    /** 携带的历史对话轮数 */
    private Integer historySize = 10;

    /** 采样温度,保险场景建议偏低以保证回答严谨 */
    private Double temperature = 0.3D;
}
