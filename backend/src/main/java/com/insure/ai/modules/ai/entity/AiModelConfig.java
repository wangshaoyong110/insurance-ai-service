package com.insure.ai.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.insure.ai.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 大模型配置
 * <p>API Key 以 AES-256-GCM 密文存储,只向界面返回掩码</p>
 *
 * @author insurance-ai
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model_config")
public class AiModelConfig extends BaseEntity {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置名称,如 通义千问-生产 */
    private String configName;

    /** 提供方:DASHSCOPE / OPENAI_COMPATIBLE */
    private String provider;

    /** 接口地址,为空时使用厂商默认地址 */
    private String baseUrl;

    /** API Key 密文,不参与 JSON 序列化,避免泄露到前端 */
    @JsonIgnore
    private String apiKeyCipher;

    /** API Key 掩码,仅用于列表展示 */
    private String apiKeyMask;

    /** 模型名称,如 qwen-plus / deepseek-chat */
    private String model;

    /** 采样温度 */
    private BigDecimal temperature;

    /** 单次回复最大 token 数 */
    private Integer maxTokens;

    /** 读超时(毫秒) */
    private Integer timeoutMs;

    /** 是否默认配置:1-是 0-否 */
    private Integer isDefault;

    /** 状态:1-启用 0-停用 */
    private Integer status;
}
