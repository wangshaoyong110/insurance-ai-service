package com.insure.ai.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.insure.ai.modules.ai.entity.AiModelConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 大模型配置 Mapper
 *
 * @author insurance-ai
 */
@Mapper
public interface AiModelConfigMapper extends BaseMapper<AiModelConfig> {
}
