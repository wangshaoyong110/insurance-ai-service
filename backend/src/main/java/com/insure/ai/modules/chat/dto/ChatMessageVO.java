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
    /** 角色:user / assistant / staff / system */
    private String role;
    /** 消息类型:text/image/video/system */
    private String msgType;
    /** 消息内容 */
    private String content;
    /** 媒体地址(图片/视频) */
    private String mediaUrl;
    /** 原始文件名 */
    private String mediaName;
    /** 文件大小(字节) */
    private Long mediaSize;
    /** 发送者显示名(坐席姓名) */
    private String senderName;
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
        vo.setMsgType(message.getMsgType());
        vo.setContent(message.getContent());
        vo.setMediaUrl(message.getMediaUrl());
        vo.setMediaName(message.getMediaName());
        vo.setMediaSize(message.getMediaSize());
        vo.setSenderName(message.getSenderName());
        vo.setModel(message.getModel());
        vo.setTokens(message.getTokens());
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }
}
