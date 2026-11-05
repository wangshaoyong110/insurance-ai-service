package com.insure.ai.modules.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.insure.ai.common.BaseEntity;
import com.insure.ai.common.serializer.PhoneSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AI 回答评价实体
 * <p>同一条消息同一客户只允许一条评价记录，重复提交做覆盖更新</p>
 *
 * @author insurance-ai
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_feedback")
public class ChatFeedback extends BaseEntity {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被评价的 AI 消息ID */
    private Long messageId;

    /** 会话ID */
    private Long sessionId;

    /** 评价客户ID */
    private Long cusUserId;

    /** 评价:1-有用 0-没用 */
    private Integer rating;

    /** 差评原因 */
    private String reason;

    /** 补充说明 */
    private String content;

    /* ---------------- 非数据库字段(管理端列表回填) ---------------- */

    /** 客户展示名 */
    @TableField(exist = false)
    private String customerName;

    /** 客户手机号(出口自动打码) */
    @TableField(exist = false)
    @JsonSerialize(using = PhoneSerializer.class)
    private String customerPhone;

    /** AI 回复内容片段(便于坐席判断问题) */
    @TableField(exist = false)
    private String answerPreview;

    /** 客户当时的提问 */
    @TableField(exist = false)
    private String question;

    /** 评价时间(直接暴露 createTime 的别名,前端表格列更好写) */
    @TableField(exist = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime feedbackTime;
}
