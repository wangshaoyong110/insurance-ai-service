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

    /**
     * 会话服务模式:0-AI 2-人工接入中
     * <p>人工接入时 answer 为空,前端不应再等待 AI 回复,改为轮询坐席消息</p>
     */
    private Integer serviceMode;

    /**
     * 是否需要先完成实名认证
     * <p>未实名客户询问本人保单/理赔等业务数据时为 true,answer 里是引导话术,
     * 前端据此展示「去实名认证」按钮</p>
     */
    private Boolean needRealName;
}
