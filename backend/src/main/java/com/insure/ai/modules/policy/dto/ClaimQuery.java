package com.insure.ai.modules.policy.dto;

import com.insure.ai.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 理赔列表查询参数
 *
 * @author insurance-ai
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ClaimQuery extends PageQuery {

    /** 理赔案号(模糊) */
    private String claimNo;

    /** 保单号(模糊) */
    private String policyNo;

    /** 客户关键字:姓名 / 手机号(模糊) */
    private String customer;

    /** 理赔类型 */
    private String claimType;

    /** 状态 */
    private Integer status;
}
