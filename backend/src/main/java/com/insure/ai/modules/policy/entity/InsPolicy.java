package com.insure.ai.modules.policy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.insure.ai.common.BaseEntity;
import com.insure.ai.common.serializer.IdCardSerializer;
import com.insure.ai.common.serializer.PhoneSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 保单实体
 * <p>cus_user_id 是数据隔离的唯一依据:客户侧的查询一律带上该条件,绝不接受外部传入</p>
 *
 * @author insurance-ai
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ins_policy")
public class InsPolicy extends BaseEntity {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 保单号 */
    private String policyNo;

    /** 所属客户ID */
    private Long cusUserId;

    /** 产品名称 */
    private String productName;

    /** 产品代码 */
    private String productCode;

    /** 险种:CRITICAL/MEDICAL/ACCIDENT/LIFE/ANNUITY/OTHER */
    private String category;

    /** 投保人姓名 */
    private String holderName;

    /** 被保人姓名 */
    private String insuredName;

    /**
     * 被保人证件号
     * <p>库中明文存储，出口一律由 {@link IdCardSerializer} 打码，避免对接真实业务库后敏感信息直发前端</p>
     */
    @JsonSerialize(using = IdCardSerializer.class)
    private String insuredIdCard;

    /** 年缴保费 */
    private BigDecimal premium;

    /** 保额 */
    private BigDecimal coverageAmount;

    /** 缴费年限,空表示趸交 */
    private Integer payYears;

    /** 生效日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveDate;

    /** 保险期间终止日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expireDate;

    /** 下次缴费日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate nextPayDate;

    /** 状态:1-有效 2-中止 3-失效 4-已退保 5-满期终止 */
    private Integer status;

    /** 客户姓名(非表字段,列表回填) */
    @TableField(exist = false)
    private String customerName;

    /** 客户手机号(非表字段,列表回填) */
    @TableField(exist = false)
    @JsonSerialize(using = PhoneSerializer.class)
    private String customerPhone;

    /** 理赔次数(非表字段,列表回填) */
    @TableField(exist = false)
    private Long claimCount;
}
