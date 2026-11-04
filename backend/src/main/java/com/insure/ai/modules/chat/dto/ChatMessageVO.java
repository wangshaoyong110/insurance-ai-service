package com.insure.ai.modules.chat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.insure.ai.modules.chat.entity.ChatMessage;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话消息返回对象(前端聊天气泡数据)
 *
 * @author insurance-ai
 */
@Data
public class ChatMessageVO implements Serializable {

    /** 消息ID */
    private Long id;
    /** 会话ID */
    private Long sessionId;
    /** 角色:user / assistant */
    private String role;
    /** 消息内容 */
    private String content;
    /** 生成该消息所使用的模型(仅 AI 消息有值) */
    private String model;
    /** 消耗的 token 数(仅 AI 消息有值) */
    private Integer tokens;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    public static ChatMessageVO from(ChatMessage message) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(message.getId());
        vo.setSessionId(message.getSessionId());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setModel(message.getModel());
        vo.setTokens(message.getTokens());
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }
}
