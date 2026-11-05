package com.insure.ai.modules.chat.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.Constants;
import com.insure.ai.common.PageResult;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.ChatMessageTypeEnum;
import com.insure.ai.common.enums.ChatRoleEnum;
import com.insure.ai.common.enums.CommonStatusEnum;
import com.insure.ai.common.enums.ServiceModeEnum;
import com.insure.ai.modules.chat.dto.ChatMessageVO;
import com.insure.ai.modules.chat.dto.ChatSessionVO;
import com.insure.ai.modules.chat.entity.ChatMessage;
import com.insure.ai.modules.chat.entity.ChatSession;
import com.insure.ai.modules.chat.mapper.ChatMessageMapper;
import com.insure.ai.modules.chat.mapper.ChatSessionMapper;
import com.insure.ai.modules.chat.service.ChatSessionService;
import com.insure.ai.modules.chat.support.ChatTextUtils;
import com.insure.ai.modules.kb.service.KbQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 会话与消息存取实现
 *
 * @author insurance-ai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {

    /** 会话摘要最大长度 */
    private static final int SUMMARY_MAX_LENGTH = 200;
    /** 消息分页默认条数 */
    private static final long DEFAULT_MESSAGE_PAGE_SIZE = 50L;
    /** 消息分页上限 */
    private static final long MAX_MESSAGE_PAGE_SIZE = 200L;
    /** 坐席一次最多拉取的会话消息条数 */
    private static final int ADMIN_MESSAGE_LIMIT = 200;
    /** 增量轮询单次最多返回条数 */
    private static final int POLL_MESSAGE_LIMIT = 100;

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final KbQuestionService kbQuestionService;

    @Override
    public List<ChatSessionVO> listSessions(Long cusUserId) {
        List<ChatSession> sessions = chatSessionMapper.selectList(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getCusUserId, cusUserId)
                .orderByDesc(ChatSession::getLastMessageTime)
                .orderByDesc(ChatSession::getId));
        return sessions.stream().map(ChatSessionVO::from).toList();
    }

    @Override
    public PageResult<ChatMessageVO> listMessages(Long cusUserId, Long sessionId, Long pageNum, Long pageSize) {
        ChatSession session = requireOwnSession(cusUserId, sessionId);
        long current = pageNum == null || pageNum < 1 ? 1L : pageNum;
        long size = pageSize == null || pageSize < 1
                ? DEFAULT_MESSAGE_PAGE_SIZE : Math.min(pageSize, MAX_MESSAGE_PAGE_SIZE);
        Page<ChatMessage> page = chatMessageMapper.selectPage(new Page<>(current, size),
                Wrappers.<ChatMessage>lambdaQuery()
                        .eq(ChatMessage::getSessionId, session.getId())
                        .orderByDesc(ChatMessage::getId));
        List<ChatMessageVO> records = new ArrayList<>(page.getRecords().stream()
                .map(ChatMessageVO::from).toList());
        // 倒序取的是"最近 N 条"，翻回正序才能按对话顺序展示
        Collections.reverse(records);
        return new PageResult<>(records, page.getTotal(), current, size);
    }

    @Override
    public ChatSessionVO createSession(Long cusUserId, String title) {
        ChatSession session = new ChatSession();
        session.setCusUserId(cusUserId);
        session.setTitle(StringUtils.hasText(title)
                ? ChatTextUtils.truncate(title, Constants.SESSION_TITLE_MAX_LENGTH) : "新会话");
        session.setMessageCount(0);
        session.setLastMessage("暂无消息");
        session.setLastMessageTime(LocalDateTime.now());
        chatSessionMapper.insert(session);
        return ChatSessionVO.from(session);
    }

    @Override
    public ChatSessionVO sessionDetail(Long cusUserId, Long sessionId) {
        return ChatSessionVO.from(requireOwnSession(cusUserId, sessionId));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteSession(Long cusUserId, Long sessionId) {
        requireOwnSession(cusUserId, sessionId);
        chatSessionMapper.deleteById(sessionId);
        chatMessageMapper.delete(Wrappers.<ChatMessage>lambdaQuery().eq(ChatMessage::getSessionId, sessionId));
    }

    @Override
    public void renameSession(Long cusUserId, Long sessionId, String title) {
        ChatSession session = requireOwnSession(cusUserId, sessionId);
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "会话标题不能为空");
        }
        ChatSession update = new ChatSession();
        update.setId(session.getId());
        update.setTitle(ChatTextUtils.truncate(title.trim(), Constants.SESSION_TITLE_MAX_LENGTH));
        chatSessionMapper.updateById(update);
    }

    @Override
    public ChatSession resolveSession(Long cusUserId, Long sessionId, String titleSeed) {
        if (sessionId == null) {
            ChatSession session = new ChatSession();
            session.setCusUserId(cusUserId);
            session.setTitle(ChatTextUtils.truncate(titleSeed, Constants.SESSION_TITLE_MAX_LENGTH));
            session.setServiceMode(ServiceModeEnum.AI.getCode());
            session.setMessageCount(0);
            session.setLastMessage(ChatTextUtils.truncate(titleSeed, SUMMARY_MAX_LENGTH));
            session.setLastMessageTime(LocalDateTime.now());
            chatSessionMapper.insert(session);
            return session;
        }
        return requireOwnSession(cusUserId, sessionId);
    }

    @Override
    public ChatSession requireSession(Long sessionId) {
        ChatSession session = sessionId == null ? null : chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.CHAT_SESSION_NOT_EXIST);
        }
        return session;
    }

    @Override
    public ChatSession requireOwnSession(Long cusUserId, Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.CHAT_SESSION_NOT_EXIST);
        }
        if (!session.getCusUserId().equals(cusUserId)) {
            throw new BusinessException(ResultCode.CHAT_SESSION_NO_PERMISSION);
        }
        return session;
    }

    @Override
    public ChatMessage appendMessage(Long sessionId, Long cusUserId, ChatRoleEnum role, ChatMessageTypeEnum type,
                                     String content, String mediaUrl, String mediaName, Long mediaSize,
                                     String senderName, String model, Integer tokens) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setCusUserId(cusUserId);
        message.setRole(role.getCode());
        message.setMsgType(type.getCode());
        message.setContent(content == null ? "" : content);
        message.setMediaUrl(mediaUrl);
        message.setMediaName(mediaName);
        message.setMediaSize(mediaSize == null ? 0L : mediaSize);
        message.setSenderName(senderName);
        message.setModel(model);
        message.setTokens(tokens == null ? 0 : tokens);
        chatMessageMapper.insert(message);
        touchSession(sessionId, ChatTextUtils.previewOf(message));
        return message;
    }

    @Override
    public ChatMessage saveMessage(Long sessionId, Long cusUserId, ChatRoleEnum role,
                                   String content, String model, Integer tokens) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setCusUserId(cusUserId);
        message.setRole(role.getCode());
        message.setMsgType(ChatMessageTypeEnum.TEXT.getCode());
        message.setContent(content);
        message.setMediaSize(0L);
        message.setModel(model);
        message.setTokens(tokens == null ? 0 : tokens);
        chatMessageMapper.insert(message);
        return message;
    }

    @Override
    public void updateSessionSummary(ChatSession session, String question, String answer) {
        ChatSession update = new ChatSession();
        update.setId(session.getId());
        update.setLastMessage(ChatTextUtils.truncate(answer, SUMMARY_MAX_LENGTH));
        update.setLastMessageTime(LocalDateTime.now());
        update.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + 2);
        if (!StringUtils.hasText(session.getTitle()) || "新会话".equals(session.getTitle())) {
            update.setTitle(ChatTextUtils.truncate(question, Constants.SESSION_TITLE_MAX_LENGTH));
        }
        chatSessionMapper.updateById(update);
        // 同步回内存对象,便于返回给前端
        session.setTitle(update.getTitle() == null ? session.getTitle() : update.getTitle());
        session.setMessageCount(update.getMessageCount());
        session.setLastMessageTime(update.getLastMessageTime());
    }

    @Override
    public List<ChatMessageVO> listMessagesAfter(Long cusUserId, Long sessionId, Long afterId) {
        ChatSession session = requireOwnSession(cusUserId, sessionId);
        return queryAfter(session.getId(), afterId);
    }

    @Override
    public List<ChatMessageVO> adminMessages(Long sessionId, Long afterId) {
        requireSession(sessionId);
        if (afterId != null && afterId > 0) {
            return queryAfter(sessionId, afterId);
        }
        List<ChatMessage> list = chatMessageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + ADMIN_MESSAGE_LIMIT));
        List<ChatMessageVO> records = new ArrayList<>(list.stream().map(ChatMessageVO::from).toList());
        Collections.reverse(records);
        return records;
    }

    @Override
    public Map<String, Object> statistics() {
        Long sessionCount = chatSessionMapper.selectCount(null);
        Long messageCount = chatMessageMapper.selectCount(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getRole, ChatRoleEnum.USER.getCode()));
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        Long todaySession = chatSessionMapper.selectCount(Wrappers.<ChatSession>lambdaQuery()
                .ge(ChatSession::getCreateTime, startOfDay));
        Long activeKb = kbQuestionService.listEnabled().stream()
                .filter(item -> CommonStatusEnum.ENABLE.getCode().equals(item.getStatus()))
                .count();
        return Map.of(
                "sessionCount", sessionCount,
                "questionCount", messageCount,
                "todaySession", todaySession,
                "kbCount", activeKb);
    }

    /**
     * 更新会话的最后消息与条数
     */
    @Override
    public void touchSession(Long sessionId, String preview) {
        ChatSession current = chatSessionMapper.selectById(sessionId);
        ChatSession update = new ChatSession();
        update.setId(sessionId);
        update.setLastMessage(ChatTextUtils.truncate(preview, SUMMARY_MAX_LENGTH));
        update.setLastMessageTime(LocalDateTime.now());
        update.setMessageCount((current == null || current.getMessageCount() == null
                ? 0 : current.getMessageCount()) + 1);
        chatSessionMapper.updateById(update);
    }

    /**
     * 取 afterId 之后的增量消息
     */
    private List<ChatMessageVO> queryAfter(Long sessionId, Long afterId) {
        long cursor = afterId == null || afterId < 0 ? 0L : afterId;
        List<ChatMessage> list = chatMessageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getSessionId, sessionId)
                .gt(ChatMessage::getId, cursor)
                .orderByAsc(ChatMessage::getId)
                .last("LIMIT " + POLL_MESSAGE_LIMIT));
        return list.stream().map(ChatMessageVO::from).toList();
    }
}
