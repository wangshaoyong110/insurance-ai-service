package com.insure.ai.modules.system.dto;

import com.insure.ai.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端用户列表查询参数
 *
 * @author insurance-ai
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQuery extends PageQuery {

    /** 关键字:账号 / 姓名 / 手机号 */
    private String keyword;

    /** 角色ID */
    private Long roleId;

    /** 状态:1-启用 0-禁用 */
    private Integer status;
}
