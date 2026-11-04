package com.insure.ai.modules.kb.dto;

import com.insure.ai.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库查询参数
 *
 * @author insurance-ai
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KbQuery extends PageQuery {

    /** 关键字:问题 / 答案 / 关键词 */
    private String keyword;

    /** 业务分类 */
    private String category;

    /** 状态 */
    private Integer status;
}
