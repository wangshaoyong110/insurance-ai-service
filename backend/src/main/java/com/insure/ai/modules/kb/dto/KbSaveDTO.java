package com.insure.ai.modules.kb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 知识库新增/编辑参数
 *
 * @author insurance-ai
 */
@Data
public class KbSaveDTO implements Serializable {

    /** 主键ID,新增时为空 */
    private Long id;

    /** 业务分类 */
    @NotBlank(message = "请选择业务分类")
    @Pattern(regexp = "INSURE|POLICY|SURRENDER|REDUCE|CLAIM|OTHER", message = "业务分类不合法")
    private String category;

    /** 标准问题 */
    @NotBlank(message = "请输入标准问题")
    @Size(max = 500, message = "问题长度不能超过 500 个字符")
    private String question;

    /** 标准答案 */
    @NotBlank(message = "请输入标准答案")
    private String answer;

    /** 关键词,逗号分隔 */
    @Size(max = 255, message = "关键词长度不能超过 255 个字符")
    private String keywords;

    /** 状态:1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
