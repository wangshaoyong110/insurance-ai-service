package com.insure.ai.modules.policy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.insure.ai.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 理赔案件实体
 *
 * @author insurance-ai
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ins_claim")
public class InsClaim extends BaseEntity {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 理赔案号 */
    private String claimNo;

    /** 所属客户ID */
    private Long cusUserId;

    /** 关联保单ID */
    private Long policyId;

    /** 关联保单号(冗余) */
    private String policyNo;

    /** 理赔类型:MEDICAL/CRITICAL/ACCIDENT/DEATH */
    private String claimType;

    /** 出险日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate accidentDate;

    /** 报案日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate reportDate;

    /** 申请赔付金额 */
    private BigDecimal claimAmount;

    /** 核定赔付金额,未结案为空 */
    private BigDecimal approvedAmount;

    /** 状态:1-已报案 2-资料审核 3-调查中 4-审核通过 5-已赔付 6-拒赔 7-已撤销 */
    private Integer status;

    /** 当前所处环节 */
    private String currentNode;

    /** 当前处理人 */
    private String handler;

    /** 预计赔付日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expectedPayDate;

    /** 结案日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate closeDate;

    /** 拒赔原因 */
    private String rejectReason;

    /** 客户姓名(非表字段,列表回填) */
    @TableField(exist = false)
    private String customerName;

    /** 客户手机号(非表字段,列表回填) */
    @TableField(exist = false)
    private String customerPhone;

    /** 产品名称(非表字段,列表回填) */
    @TableField(exist = false)
    private String productName;
}
