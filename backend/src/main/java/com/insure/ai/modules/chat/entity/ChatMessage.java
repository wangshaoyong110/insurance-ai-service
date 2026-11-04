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

    /** 角色:user-用户 assistant-AI */
    private String role;

    /** 消息内容 */
    private String content;

    /** 模型名称 */
    private String model;

    /** 消耗 token 数 */
    private Integer tokens;
}
