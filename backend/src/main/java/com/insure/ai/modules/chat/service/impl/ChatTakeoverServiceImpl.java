package com.insure.ai.modules.chat.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.ChatMessageTypeEnum;
import com.insure.ai.common.enums.ChatRoleEnum;
import com.insure.ai.common.enums.ServiceModeEnum;
import com.insure.ai.config.UploadProperties;
import com.insure.ai.modules.chat.dto.ChatMessageSendDTO;
import com.insure.ai.modules.chat.dto.ChatMessageVO;
import com.insure.ai.modules.chat.dto.ChatSessionVO;
import com.insure.ai.modules.chat.entity.ChatMessage;
import com.insure.ai.modules.chat.entity.ChatSession;
import com.insure.ai.modules.chat.mapper.ChatSessionMapper;
import com.insure.ai.modules.chat.service.ChatSessionService;
import com.insure.ai.modules.chat.service.ChatTakeoverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 人工接管状态流转实现
 *
 * @author insurance-ai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatTakeoverServiceImpl implements ChatTakeoverService {

    private final ChatSessionService chatSessionService;
    private final ChatSessionMapper chatSessionMapper;
    private final UploadProperties uploadProperties;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ChatMessageVO sendMessage(Long cusUserId, ChatMessageSendDTO dto) {
        ChatMessageTypeEnum type = ChatMessageTypeEnum.of(dto.getMsgType());
        if (type == ChatMessageTypeEnum.SYSTEM) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不支持发送系统消息");
        }
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (type.isMedia()) {
            validateMedia(type, dto);
        } else if (!StringUtils.hasText(content)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "消息内容不能为空");
        }
        String title = StringUtils.hasText(content) ? content : "[" + type.getDesc() + "]";
        ChatSession session = chatSessionService.resolveSession(cusUserId, dto.getSessionId(), title);
        ChatMessage message = chatSessionService.appendMessage(session.getId(), cusUserId, ChatRoleEnum.USER, type,
                content, dto.getMediaUrl(), dto.getMediaName(), dto.getMediaSize(), null, null, 0);
        return ChatMessageVO.from(message);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ChatMessageVO staffSend(Long sessionId, Long operatorId, String operatorName, ChatMessageSendDTO dto) {
        ChatSession session = chatSessionService.requireSession(sessionId);
        ServiceModeEnum mode = ServiceModeEnum.of(session.getServiceMode());
        if (!mode.isHuman()) {
            throw new BusinessException(ResultCode.TICKET_STATUS_INVALID.getCode(), "请先接单后再回复客户");
        }
        if (session.getHandlerId() != null && !Objects.equals(session.getHandlerId(), operatorId)) {
            throw new BusinessException(ResultCode.TICKET_STATUS_INVALID.getCode(),
                    "该会话已由 " + session.getHandlerName() + " 接管");
        }
        ChatMessageTypeEnum type = ChatMessageTypeEnum.of(dto.getMsgType());
        if (type == ChatMessageTypeEnum.SYSTEM) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不支持发送系统消息");
        }
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (type.isMedia()) {
            validateMedia(type, dto);
        } else if (!StringUtils.hasText(content)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "消息内容不能为空");
        }
        ChatMessage message = chatSessionService.appendMessage(session.getId(), session.getCusUserId(),
                ChatRoleEnum.STAFF, type, content, dto.getMediaUrl(), dto.getMediaName(), dto.getMediaSize(),
                StringUtils.hasText(operatorName) ? operatorName : "人工客服", null, 0);
        return ChatMessageVO.from(message);
    }

    @Override
    public void bindTicket(Long cusUserId, Long sessionId, Long ticketId) {
        ChatSession session = chatSessionService.requireOwnSession(cusUserId, sessionId);
        ChatSession update = new ChatSession();
        update.setId(session.getId());
        update.setServiceMode(ServiceModeEnum.WAITING.getCode());
        update.setTicketId(ticketId);
        chatSessionMapper.updateById(update);
        chatSessionService.appendMessage(session.getId(), cusUserId, ChatRoleEnum.SYSTEM, ChatMessageTypeEnum.SYSTEM,
                "已提交人工工单,正在为你安排客服。排队期间仍可继续咨询,客服接入后会在这里回复你。",
                null, null, null, null, null, 0);
    }

    @Override
    public void takeover(Long sessionId, Long ticketId, Long operatorId, String operatorName) {
        ChatSession session = chatSessionService.requireSession(sessionId);
        ServiceModeEnum mode = ServiceModeEnum.of(session.getServiceMode());
        if (mode.isHuman() && session.getHandlerId() != null
                && !Objects.equals(session.getHandlerId(), operatorId)) {
            throw new BusinessException(ResultCode.TICKET_STATUS_INVALID.getCode(),
                    "该会话已由 " + session.getHandlerName() + " 接管");
        }
        ChatSession update = new ChatSession();
        update.setId(session.getId());
        update.setServiceMode(ServiceModeEnum.HUMAN.getCode());
        update.setHandlerId(operatorId);
        update.setHandlerName(operatorName);
        if (ticketId != null) {
            update.setTicketId(ticketId);
        }
        chatSessionMapper.updateById(update);
        chatSessionService.appendMessage(session.getId(), session.getCusUserId(), ChatRoleEnum.SYSTEM,
                ChatMessageTypeEnum.SYSTEM,
                "客服 " + operatorName + " 已接入本会话,AI 已暂停回答,你可以直接在这里沟通。",
                null, null, null, null, null, 0);
        log.info("[人工接管] 会话 {} 由 {} 接管 ticketId={}", sessionId, operatorName, ticketId);
    }

    /**
     * 补建会话并接管:建会话 + 接管两步在同一事务里,失败即整体回滚
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long createTakeoverSession(Long cusUserId, Long ticketId, String title,
                                      Long operatorId, String operatorName) {
        ChatSessionVO created = chatSessionService.createSession(cusUserId, title);
        takeover(created.getId(), ticketId, operatorId, operatorName);
        return created.getId();
    }

    @Override
    public void finishTakeover(Long sessionId, String reply, String operatorName) {
        ChatSession session = chatSessionService.requireSession(sessionId);
        if (StringUtils.hasText(reply)) {
            chatSessionService.appendMessage(session.getId(), session.getCusUserId(), ChatRoleEnum.STAFF,
                    ChatMessageTypeEnum.TEXT, reply.trim(), null, null, null,
                    StringUtils.hasText(operatorName) ? operatorName : "人工客服", null, 0);
        }
        chatSessionService.appendMessage(session.getId(), session.getCusUserId(), ChatRoleEnum.SYSTEM,
                ChatMessageTypeEnum.SYSTEM,
                "本次人工服务已结束,AI 助手已恢复。如仍有问题可以继续提问。",
                null, null, null, null, null, 0);
        releaseSession(session.getId());
    }

    @Override
    public void cancelTakeover(Long sessionId, String notice) {
        ChatSession session = chatSessionService.requireSession(sessionId);
        chatSessionService.appendMessage(session.getId(), session.getCusUserId(), ChatRoleEnum.SYSTEM,
                ChatMessageTypeEnum.SYSTEM,
                StringUtils.hasText(notice) ? notice : "人工工单已取消,AI 助手已恢复服务。",
                null, null, null, null, null, 0);
        releaseSession(session.getId());
    }

    /**
     * 会话交回 AI
     */
    private void releaseSession(Long sessionId) {
        // 必须用 UpdateWrapper 显式 set null:updateById 默认忽略 null 字段,
        // 否则 handler_id / handler_name 会一直留着上一任坐席,影响下次接管的归属判断
        chatSessionMapper.update(null, Wrappers.<ChatSession>lambdaUpdate()
                .eq(ChatSession::getId, sessionId)
                .set(ChatSession::getServiceMode, ServiceModeEnum.AI.getCode())
                .set(ChatSession::getHandlerId, null)
                .set(ChatSession::getHandlerName, null));
    }

    /**
     * 校验客户上传的媒体地址
     * <p>只接受本站上传返回的地址,防止把任意外链塞进会话变成钓鱼图片或追踪像素</p>
     */
    private void validateMedia(ChatMessageTypeEnum type, ChatMessageSendDTO dto) {
        String url = dto.getMediaUrl();
        String prefix = uploadProperties.getUrlPrefix();
        if (!StringUtils.hasText(url)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请先上传" + type.getDesc());
        }
        if (!StringUtils.hasText(prefix) || !url.startsWith(prefix) || url.contains("..")) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "媒体地址不合法,请重新上传");
        }
        long max = uploadProperties.getMaxSize() == null ? 0 : uploadProperties.getMaxSize().toBytes();
        if (dto.getMediaSize() != null && max > 0 && dto.getMediaSize() > max) {
            throw new BusinessException(ResultCode.FILE_SIZE_EXCEEDED.getCode(),
                    "文件大小超出限制(最大 " + uploadProperties.maxSizeText() + ")");
        }
    }
}
