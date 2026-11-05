package com.insure.ai.modules.chat.service.impl;

import com.insure.ai.common.PageResult;
import com.insure.ai.modules.chat.dto.ChatAnswerVO;
import com.insure.ai.modules.chat.dto.ChatMessageSendDTO;
import com.insure.ai.modules.chat.dto.ChatMessageVO;
import com.insure.ai.modules.chat.dto.ChatSendDTO;
import com.insure.ai.modules.chat.dto.ChatSessionVO;
import com.insure.ai.modules.chat.service.ChatSendService;
import com.insure.ai.modules.chat.service.ChatService;
import com.insure.ai.modules.chat.service.ChatSessionService;
import com.insure.ai.modules.chat.service.ChatTakeoverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 智能客服会话服务门面
 * <p>
 * 本类只做转发,不含任何业务逻辑。实现按职责拆成三块:
 * <ul>
 *     <li>{@link ChatSessionService} —— 会话增删改查、消息读写、归属校验</li>
 *     <li>{@link ChatSendService} —— 知识库召回、模型调用、SSE 流式推送</li>
 *     <li>{@link ChatTakeoverService} —— AI/排队/人工 三态流转与人工在场时的双向消息</li>
 * </ul>
 * 保留门面而非让调用方直连三个服务,是为了让控制器与工单模块只依赖一个入口,
 * 将来再拆细分服务时不必逐个改调用点。
 * </p>
 *
 * @author insurance-ai
 */
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatSessionService chatSessionService;
    private final ChatSendService chatSendService;
    private final ChatTakeoverService chatTakeoverService;

    /* ================= 会话与消息读写 ================= */

    @Override
    public List<ChatSessionVO> listSessions(Long cusUserId) {
        return chatSessionService.listSessions(cusUserId);
    }

    @Override
    public PageResult<ChatMessageVO> listMessages(Long cusUserId, Long sessionId, Long pageNum, Long pageSize) {
        return chatSessionService.listMessages(cusUserId, sessionId, pageNum, pageSize);
    }

    @Override
    public ChatSessionVO createSession(Long cusUserId, String title) {
        return chatSessionService.createSession(cusUserId, title);
    }

    @Override
    public ChatSessionVO sessionDetail(Long cusUserId, Long sessionId) {
        return chatSessionService.sessionDetail(cusUserId, sessionId);
    }

    @Override
    public void deleteSession(Long cusUserId, Long sessionId) {
        chatSessionService.deleteSession(cusUserId, sessionId);
    }

    @Override
    public void renameSession(Long cusUserId, Long sessionId, String title) {
        chatSessionService.renameSession(cusUserId, sessionId, title);
    }

    @Override
    public List<ChatMessageVO> listMessagesAfter(Long cusUserId, Long sessionId, Long afterId) {
        return chatSessionService.listMessagesAfter(cusUserId, sessionId, afterId);
    }

    @Override
    public List<ChatMessageVO> adminMessages(Long sessionId, Long afterId) {
        return chatSessionService.adminMessages(sessionId, afterId);
    }

    @Override
    public Map<String, Object> statistics() {
        return chatSessionService.statistics();
    }

    /* ================= AI 问答链路 ================= */

    @Override
    public ChatAnswerVO send(Long cusUserId, ChatSendDTO dto) {
        return chatSendService.send(cusUserId, dto);
    }

    @Override
    public SseEmitter sendStream(Long cusUserId, ChatSendDTO dto) {
        return chatSendService.sendStream(cusUserId, dto);
    }

    /* ================= 人工接管 ================= */

    @Override
    public ChatMessageVO sendMessage(Long cusUserId, ChatMessageSendDTO dto) {
        return chatTakeoverService.sendMessage(cusUserId, dto);
    }

    @Override
    public ChatMessageVO staffSend(Long sessionId, Long operatorId, String operatorName, ChatMessageSendDTO dto) {
        return chatTakeoverService.staffSend(sessionId, operatorId, operatorName, dto);
    }

    @Override
    public void bindTicket(Long cusUserId, Long sessionId, Long ticketId) {
        chatTakeoverService.bindTicket(cusUserId, sessionId, ticketId);
    }

    @Override
    public void takeover(Long sessionId, Long ticketId, Long operatorId, String operatorName) {
        chatTakeoverService.takeover(sessionId, ticketId, operatorId, operatorName);
    }

    @Override
    public Long createTakeoverSession(Long cusUserId, Long ticketId, String title,
                                      Long operatorId, String operatorName) {
        return chatTakeoverService.createTakeoverSession(cusUserId, ticketId, title, operatorId, operatorName);
    }

    @Override
    public void finishTakeover(Long sessionId, String reply, String operatorName) {
        chatTakeoverService.finishTakeover(sessionId, reply, operatorName);
    }

    @Override
    public void cancelTakeover(Long sessionId, String notice) {
        chatTakeoverService.cancelTakeover(sessionId, notice);
    }
}
