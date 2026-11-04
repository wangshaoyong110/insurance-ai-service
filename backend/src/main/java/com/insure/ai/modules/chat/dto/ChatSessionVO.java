package com.insure.ai.modules.chat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.insure.ai.modules.chat.entity.ChatSession;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话列表返回对象
 *
 * @author insurance-ai
 */
@Data
public class ChatSessionVO implements Serializable {

    /** 会话ID */
    private Long id;
    /** 会话标题 */
    private String title;
    /** 最后一条消息摘要 */
    private String lastMessage;
    /** 消息条数 */
    private Integer messageCount;
    /** 最后消息时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastMessageTime;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    public static ChatSessionVO from(ChatSession session) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.setId(session.getId());
        vo.setTitle(session.getTitle());
        vo.setLastMessage(session.getLastMessage());
        vo.setMessageCount(session.getMessageCount());
        vo.setLastMessageTime(session.getLastMessageTime());
        vo.setCreateTime(session.getCreateTime());
        return vo;
    }
}
