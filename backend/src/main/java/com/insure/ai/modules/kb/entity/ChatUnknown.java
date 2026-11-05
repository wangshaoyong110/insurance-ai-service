package com.insure.ai.modules.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.insure.ai.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识盲区记录实体
 * <p>知识库召回为空或仅弱命中时落一条，后台按问题聚合后由运营决定补语料还是忽略</p>
 *
 * @author insurance-ai
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_unknown")
public class ChatUnknown extends BaseEntity {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提问客户ID */
    private Long cusUserId;

    /** 会话ID */
    private Long sessionId;

    /** 未命中问题原文 */
    private String question;

    /** 知识库最高召回得分,0 表示完全没命中 */
    private Integer bestScore;

    /** 状态:0-待处理 1-已转知识 2-已忽略 */
    private Integer status;

    /** 转化后的知识条目ID */
    private Long kbId;
}
