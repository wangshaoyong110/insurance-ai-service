package com.insure.ai.modules.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.insure.ai.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 保险知识库(标准问答)实体
 *
 * @author insurance-ai
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_question")
public class KbQuestion extends BaseEntity {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务分类:INSURE/POLICY/SURRENDER/REDUCE/CLAIM/OTHER */
    private String category;

    /** 标准问题 */
    private String question;

    /** 标准答案 */
    private String answer;

    /** 关键词(逗号分隔) */
    private String keywords;

    /** 命中次数 */
    private Integer hitCount;

    /** 状态:1-启用 0-停用 */
    private Integer status;
}
