package com.insure.ai.modules.ai.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 模型连通性测试参数
 * <p>
 * 支持两种用法:传入 id 测已保存的配置(apiKey 留空则用库里的密文解密);
 * 或者不传 id,直接带入 provider/baseUrl/apiKey/model 测未保存的草稿配置。
 * </p>
 *
 * @author insurance-ai
 */
@Data
public class AiModelTestDTO implements Serializable {

    /** 已保存的配置ID,可为空 */
    private Long id;

    /** 提供方 */
    private String provider;

    /** 接口地址 */
    private String baseUrl;

    /** API Key 明文,为空且 id 存在时使用库中已保存的 Key */
    private String apiKey;

    /** 模型名称 */
    private String model;
}
