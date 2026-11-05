package com.insure.ai.modules.service.service;

import com.insure.ai.common.PageResult;
import com.insure.ai.modules.service.dto.FeedbackDTO;
import com.insure.ai.modules.service.dto.FeedbackQuery;
import com.insure.ai.modules.service.entity.ChatFeedback;
import java.util.List;
import java.util.Map;

/**
 * AI 回答评价服务
 * <p>
 * 为什么需要它:没有评价数据时,"回答质量"只能靠感觉判断——模型换了、提示词改了、
 * 知识库补了,到底有没有变好无从验证。落库后至少能得到好评率与差评归因两个可跟踪指标。
 * </p>
 *
 * @author insurance-ai
 */
public interface FeedbackService {

    /**
     * 提交评价(同一消息重复提交做覆盖更新)
     */
    void submit(Long cusUserId, FeedbackDTO dto);

    /**
     * 查询某条消息的评价(前端刷新页面后回显)
     */
    ChatFeedback ofMessage(Long cusUserId, Long messageId);

    /**
     * 管理端评价分页(差评优先展示,便于运营先看问题)
     */
    PageResult<ChatFeedback> page(FeedbackQuery query);

    /**
     * 评价统计:好评率、差评数、差评原因分布
     */
    Map<String, Object> statistics();
}
