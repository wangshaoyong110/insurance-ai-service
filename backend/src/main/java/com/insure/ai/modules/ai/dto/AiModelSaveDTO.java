package com.insure.ai.modules.ai.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 大模型配置新增/编辑参数
 *
 * @author insurance-ai
 */
@Data
public class AiModelSaveDTO implements Serializable {

    /** 配置ID,新增时为空 */
    private Long id;

    /** 配置名称 */
    @NotBlank(message = "请输入配置名称")
    @Size(max = 64, message = "配置名称不能超过 64 个字符")
    private String configName;

    /** 提供方:DASHSCOPE / OPENAI_COMPATIBLE */
    @NotBlank(message = "请选择提供方")
    private String provider;

    /**
     * 接口地址
     * <p>留空则使用厂商默认地址;内网部署时填行内模型网关地址</p>
     */
    @Size(max = 255, message = "接口地址不能超过 255 个字符")
    private String baseUrl;

    /**
     * API Key 明文
     * <p>编辑时留空表示保持原 Key 不变;新增时必填</p>
     */
    @Size(max = 500, message = "API Key 长度不合法")
    private String apiKey;

    /** 模型名称 */
    @NotBlank(message = "请输入模型名称")
    @Size(max = 64, message = "模型名称不能超过 64 个字符")
    private String model;

    /** 采样温度 */
    @DecimalMin(value = "0.0", message = "温度不能小于 0")
    @DecimalMax(value = "2.0", message = "温度不能大于 2")
    private BigDecimal temperature;

    /** 最大 token 数 */
    @Min(value = 1, message = "最大 token 数不合法")
    @Max(value = 100000, message = "最大 token 数不合法")
    private Integer maxTokens;

    /** 读超时(毫秒) */
    @Min(value = 1000, message = "超时时间不能小于 1000 毫秒")
    @Max(value = 300000, message = "超时时间不能大于 300000 毫秒")
    private Integer timeoutMs;

    /** 是否默认配置:1-是 0-否 */
    private Integer isDefault;

    /** 状态:1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;
}
