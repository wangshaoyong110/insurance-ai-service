package com.insure.ai.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.insure.ai.modules.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话消息 Mapper
 *
 * @author insurance-ai
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
