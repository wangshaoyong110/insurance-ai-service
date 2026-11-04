package com.insure.ai.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.insure.ai.modules.chat.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 会话 Mapper
 *
 * @author insurance-ai
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /**
     * 统计每个客户的历史会话数量(客户列表页使用)
     *
     * @return key: cusUserId, value: sessionCount
     */
    @Select("""
            SELECT cus_user_id AS cusUserId, COUNT(*) AS sessionCount
            FROM chat_session
            WHERE deleted = 0
            GROUP BY cus_user_id
            """)
    List<Map<String, Object>> countGroupByCusUser();
}
