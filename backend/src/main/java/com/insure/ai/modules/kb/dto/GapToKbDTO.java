package com.insure.ai.modules.kb.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 知识缺口转知识库参数
 *
 * @author insurance-ai
 */
@Data
@Schema(description = "缺口转知识库参数")
public class GapToKbDTO implements Serializable {

    /** 业务分类 */
    @NotBlank(message = "请选择业务分类")
    @Pattern(regexp = "INSURE|POLICY|SURRENDER|REDUCE|CLAIM|OTHER", message = "业务分类不合法")
    @Schema(description = "业务分类", example = "CLAIM")
    private String category;

    /** 标准答案 */
    @NotBlank(message = "请填写标准答案")
    @Schema(description = "标准答案")
    private String answer;

    /** 关键词,逗号分隔 */
    @Size(max = 255, message = "关键词长度不能超过 255 个字符")
    @Schema(description = "关键词,逗号分隔")
    private String keywords;
}
