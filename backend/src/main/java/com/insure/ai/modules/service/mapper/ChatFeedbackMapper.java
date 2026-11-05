package com.insure.ai.modules.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.insure.ai.modules.service.entity.ChatFeedback;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 回答评价 Mapper
 *
 * @author insurance-ai
 */
@Mapper
public interface ChatFeedbackMapper extends BaseMapper<ChatFeedback> {
}
