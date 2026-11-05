package com.insure.ai.modules.ai.service;

import com.insure.ai.modules.ai.dto.AiModelSaveDTO;
import com.insure.ai.modules.ai.dto.AiModelTestDTO;
import com.insure.ai.modules.ai.entity.AiModelConfig;
import java.util.List;
import java.util.Map;

/**
 * 大模型配置服务
 * <p>API Key 一律以 SM4-GCM 密文入库,对外只暴露掩码</p>
 *
 * @author insurance-ai
 */
public interface AiModelConfigService {

    /**
     * 配置列表(按默认优先、创建时间倒序)
     */
    List<AiModelConfig> list();

    /**
     * 新增或编辑配置
     */
    Long save(AiModelSaveDTO dto);

    /**
     * 删除配置(逻辑删除)
     */
    void delete(Long id);

    /**
     * 启用 / 停用
     */
    void changeStatus(Long id, Integer status);

    /**
     * 设为默认配置
     */
    void setDefault(Long id);

    /**
     * 连通性测试
     *
     * @return 含耗时与模型回复的结果
     */
    Map<String, Object> test(AiModelTestDTO dto);

    /**
     * 查询并校验配置存在
     */
    AiModelConfig requireConfig(Long id);
}
