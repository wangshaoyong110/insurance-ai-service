package com.insure.ai.modules.service.dto;

import com.insure.ai.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工单分页查询参数(管理端)
 *
 * @author insurance-ai
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "工单分页查询参数")
public class TicketQuery extends PageQuery {

    /** 工单号 */
    @Schema(description = "工单号")
    private String ticketNo;

    /** 状态 */
    @Schema(description = "状态:0-待处理 1-处理中 2-已完成 3-已取消")
    private Integer status;

    /** 转人工原因 */
    @Schema(description = "转人工原因")
    private String reason;

    /** 客户关键字(姓名/手机号) */
    @Schema(description = "客户关键字")
    private String customer;

    /** 开始时间 yyyy-MM-dd */
    @Schema(description = "创建时间起")
    private String startDate;

    /** 结束时间 yyyy-MM-dd */
    @Schema(description = "创建时间止")
    private String endDate;
}
