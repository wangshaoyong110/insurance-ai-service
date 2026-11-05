package com.insure.ai.modules.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 工单处理参数(管理端)
 *
 * @author insurance-ai
 */
@Data
@Schema(description = "工单处理参数")
public class TicketHandleDTO implements Serializable {

    /** 目标状态:1-处理中 2-已完成 3-已取消 */
    @NotNull(message = "请选择要流转的状态")
    @Schema(description = "目标状态:1-处理中 2-已完成 3-已取消", example = "2")
    private Integer status;

    /** 处理回复,客户可见 */
    @Size(max = 1000, message = "处理回复不能超过 1000 字")
    @Schema(description = "处理回复(客户可见)")
    private String reply;

    /** 优先级:1-普通 2-紧急 */
    @Schema(description = "优先级:1-普通 2-紧急")
    private Integer priority;
}
