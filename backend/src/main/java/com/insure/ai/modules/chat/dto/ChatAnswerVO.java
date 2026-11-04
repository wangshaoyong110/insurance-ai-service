package com.insure.ai.modules.chat.dto;

import com.insure.ai.common.enums.InsuranceCategoryEnum;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 回复返回对象
 *
 * @author insurance-ai
 */
@Data
public class ChatAnswerVO implements Serializable {

    /** 会话ID(新建会话时由后端生成) */
    private Long sessionId;

    /** AI 回复内容 */
    private ChatMessageVO answer;

    /** 本次参考的知识库问题,便于前端展示"参考来源" */
    private List<String> references;

    /** 业务分类,用于前端展示标签 */
    private InsuranceCategoryEnum category;
}
