package com.insure.ai.modules.policy.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 理赔进度实体(时间轴)
 * <p>该表没有 update_by / update_time 字段,因此不继承 BaseEntity</p>
 *
 * @author insurance-ai
 */
@Data
@TableName("ins_claim_progress")
public class InsClaimProgress implements Serializable {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 理赔案件ID */
    private Long claimId;

    /** 环节名称 */
    private String node;

    /** 环节说明 */
    private String nodeDesc;

    /** 处理人 */
    private String operatorName;

    /** 发生时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime progressTime;

    /** 是否已完成:1-已完成 0-进行中 */
    private Integer finished;

    /** 备注 */
    private String remark;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 逻辑删除标记 */
    @JsonIgnore
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
