package com.insure.ai.modules.service.dto;

import com.insure.ai.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评价分页查询参数(管理端)
 *
 * @author insurance-ai
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "评价分页查询参数")
public class FeedbackQuery extends PageQuery {

    /** 评价:1-有用 0-没用 */
    @Schema(description = "评价:1-有用 0-没用")
    private Integer rating;

    /** 差评原因 */
    @Schema(description = "差评原因")
    private String reason;

    /** 客户关键字(姓名/手机号) */
    @Schema(description = "客户关键字")
    private String customer;

    /** 开始时间 yyyy-MM-dd */
    @Schema(description = "评价时间起")
    private String startDate;

    /** 结束时间 yyyy-MM-dd */
    @Schema(description = "评价时间止")
    private String endDate;
}
