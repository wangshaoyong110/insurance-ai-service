package com.insure.ai.modules.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.insure.ai.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 会话消息实体
 *
 * @author insurance-ai
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_message")
public class ChatMessage extends BaseEntity {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private Long sessionId;

    /** 所属客户ID(cus_user.id) */
    private Long cusUserId;

    /** 角色:user-用户 assistant-AI staff-人工客服 system-系统 */
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

    /** 模型名称 */
    private String model;

    /** 消耗 token 数 */
    private Integer tokens;
}
