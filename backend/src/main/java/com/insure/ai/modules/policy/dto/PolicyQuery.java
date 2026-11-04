package com.insure.ai.modules.policy.dto;

import com.insure.ai.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 保单列表查询参数
 *
 * @author insurance-ai
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PolicyQuery extends PageQuery {

    /** 保单号(模糊) */
    private String policyNo;

    /** 客户关键字:姓名 / 手机号(模糊) */
    private String customer;

    /** 险种 */
    private String category;

    /** 状态:1-有效 2-中止 3-失效 4-已退保 5-满期终止 */
    private Integer status;
}
