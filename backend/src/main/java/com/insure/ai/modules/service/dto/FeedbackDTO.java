package com.insure.ai.modules.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 回答评价参数(客户端)
 *
 * @author insurance-ai
 */
@Data
@Schema(description = "AI 回答评价参数")
public class FeedbackDTO implements Serializable {

    /** 被评价的消息ID(assistant 消息) */
    @NotNull(message = "缺少被评价的消息")
    @Schema(description = "被评价的 AI 消息ID")
    private Long messageId;

    /** 评价:1-有用 0-没用 */
    @NotNull(message = "请选择评价")
    @Min(value = 0, message = "评价取值不合法")
    @Max(value = 1, message = "评价取值不合法")
    @Schema(description = "1-有用 0-没用", example = "0")
    private Integer rating;

    /** 差评原因,点赞时可空 */
    @Schema(description = "差评原因:WRONG/IRRELEVANT/INCOMPLETE/SLOW/OTHER")
    private String reason;

    /** 补充说明 */
    @Size(max = 500, message = "补充说明不能超过 500 字")
    @Schema(description = "补充说明")
    private String content;
}
