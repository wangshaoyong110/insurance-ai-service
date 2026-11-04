package com.insure.ai.modules.system.dto;

import com.insure.ai.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 日志查询参数(登录日志 / 操作日志共用)
 *
 * @author insurance-ai
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LogQuery extends PageQuery {

    /** 关键字:账号 / 操作人 */
    private String keyword;

    /** 状态:1-成功 0-失败 */
    private Integer status;

    /** 开始时间(yyyy-MM-dd) */
    private String startDate;

    /** 结束时间(yyyy-MM-dd) */
    private String endDate;
}
