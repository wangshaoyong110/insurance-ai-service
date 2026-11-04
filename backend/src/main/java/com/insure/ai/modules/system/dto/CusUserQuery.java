package com.insure.ai.modules.system.dto;

import com.insure.ai.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户端客户列表查询参数
 *
 * @author insurance-ai
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CusUserQuery extends PageQuery {

    /** 关键字:昵称 / 姓名 / 手机号 */
    private String keyword;

    /** 状态:1-启用 0-禁用 */
    private Integer status;
}
