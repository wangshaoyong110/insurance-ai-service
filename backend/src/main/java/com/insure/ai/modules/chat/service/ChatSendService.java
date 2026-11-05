package com.insure.ai.modules.chat.service;

import com.insure.ai.modules.chat.dto.ChatAnswerVO;
import com.insure.ai.modules.chat.dto.ChatSendDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 问答链路
 * <p>
 * 负责「召回知识库 → 组装上下文 → 调用模型(可挂业务工具) → 回复落库」这一整条链路,
 * 以及流式形态下的事件推送。人工接管期间的静默判断也在这里,但不负责接管状态本身的流转。
 * </p>
 *
 * @author insurance-ai
 */
public interface ChatSendService {

    /**
     * 发送消息并获取 AI 回复
     * <p>注意:该方法不整体加事务,避免调用大模型期间长时间占用数据库连接</p>
     */
    ChatAnswerVO send(Long cusUserId, ChatSendDTO dto);

    /**
     * 流式发送消息(SSE)
     * <p>
     * 与 {@link #send} 的差异只在传输形态:校验、建会话、用户消息落库仍在请求线程内同步完成
     * (失败可正常返回 JSON 错误码),模型生成阶段放进线程池逐段推送,结束后统一落库。
     * </p>
     * <p>事件协议:{@code meta}(会话与参考来源) → {@code delta}(增量文本,可多次) → {@code done} 或 {@code error}。</p>
     */
    SseEmitter sendStream(Long cusUserId, ChatSendDTO dto);
}
