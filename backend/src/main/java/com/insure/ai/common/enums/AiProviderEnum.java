package com.insure.ai.common.enums;

import lombok.Getter;

/**
 * 大模型提供方
 * <p>
 * 内网部署时通常无法访问公网厂商接口,此时应选择 OPENAI_COMPATIBLE
 * 并把 baseUrl 指向行内模型网关 / vLLM / Ollama,只要对端兼容 OpenAI 协议即可。
 * </p>
 *
 * @author insurance-ai
 */
@Getter
public enum AiProviderEnum {

    /**
     * 阿里云百炼(DashScope):通义千问系列
     */
    DASHSCOPE("DASHSCOPE", "阿里云百炼(通义千问)", "https://dashscope.aliyuncs.com"),

    /**
     * OpenAI 兼容协议:DeepSeek、vLLM、Ollama、内网模型网关均可
     */
    OPENAI_COMPATIBLE("OPENAI_COMPATIBLE", "OpenAI 兼容接口", "https://api.openai.com");

    private final String code;
    private final String label;
    private final String defaultBaseUrl;

    AiProviderEnum(String code, String label, String defaultBaseUrl) {
        this.code = code;
        this.label = label;
        this.defaultBaseUrl = defaultBaseUrl;
    }

    /**
     * 按 code 查找,未匹配时回退到 OPENAI_COMPATIBLE
     */
    public static AiProviderEnum of(String code) {
        if (code != null) {
            for (AiProviderEnum item : values()) {
                if (item.code.equalsIgnoreCase(code)) {
                    return item;
                }
            }
        }
        return OPENAI_COMPATIBLE;
    }
}
