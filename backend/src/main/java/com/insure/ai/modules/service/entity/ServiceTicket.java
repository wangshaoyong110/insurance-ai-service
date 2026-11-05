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
 * 人工客服工单实体
 * <p>AI 无法解决时由客户发起，坐席在后台接单处理，构成服务兜底闭环</p>
 *
 * @author insurance-ai
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("svc_ticket")
public class ServiceTicket extends BaseEntity {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单号,如 TK202611040001 */
    private String ticketNo;

    /** 提单客户ID */
    private Long cusUserId;

    /** 关联会话ID */
    private Long sessionId;

    /** 客户问题摘要 */
    private String question;

    /** 业务分类 */
    private String category;

    /** 转人工原因 */
    private String reason;

    /** 状态:0-待处理 1-处理中 2-已完成 3-已取消 */
    private Integer status;

    /** 优先级:1-普通 2-紧急 */
    private Integer priority;

    /** 处理人ID */
    private Long handlerId;

    /** 处理人姓名 */
    private String handlerName;

    /** 处理回复 */
    private String reply;

    /** 接单时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime acceptTime;

    /** 完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishTime;

    /* ---------------- 非数据库字段 ---------------- */

    /** 客户展示名(列表回填) */
    @TableField(exist = false)
    private String customerName;

    /** 客户手机号(列表回填,出口自动打码) */
    @TableField(exist = false)
    @JsonSerialize(using = PhoneSerializer.class)
    private String customerPhone;
}
