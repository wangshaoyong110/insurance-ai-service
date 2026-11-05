package com.insure.ai.modules.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 转人工工单创建参数(客户端)
 *
 * @author insurance-ai
 */
@Data
@Schema(description = "转人工工单创建参数")
public class TicketCreateDTO implements Serializable {

    /** 关联会话ID,可为空 */
    @Schema(description = "关联会话ID")
    private Long sessionId;

    /** 客户问题摘要 */
    @NotBlank(message = "请描述需要人工协助的问题")
    @Size(max = 500, message = "问题描述不能超过 500 字")
    @Schema(description = "问题摘要", example = "我的理赔材料已经寄出两周,还没收到审核结果")
    private String question;

    /** 业务分类 */
    @Schema(description = "业务分类", example = "CLAIM")
    private String category;

    /** 转人工原因 */
    @Schema(description = "转人工原因:DISSATISFIED/COMPLAINT/COMPLEX/OTHER", example = "COMPLEX")
    private String reason;

    /** 优先级:1-普通 2-紧急 */
    @Schema(description = "优先级:1-普通 2-紧急", example = "1")
    private Integer priority;
}
