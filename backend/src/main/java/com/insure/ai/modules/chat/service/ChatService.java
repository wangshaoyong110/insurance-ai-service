package com.insure.ai.modules.chat.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.Constants;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.ChatRoleEnum;
import com.insure.ai.common.enums.CommonStatusEnum;
import com.insure.ai.common.enums.InsuranceCategoryEnum;
import com.insure.ai.config.DeepSeekProperties;
import com.insure.ai.modules.ai.entity.AiModelConfig;
import com.insure.ai.modules.ai.service.AiModelFactory;
import com.insure.ai.modules.ai.tool.InsuranceQueryTools;
import com.insure.ai.modules.chat.dto.ChatAnswerVO;
import com.insure.ai.modules.chat.dto.ChatMessageVO;
import com.insure.ai.modules.chat.dto.ChatSendDTO;
import com.insure.ai.modules.chat.dto.ChatSessionVO;
import com.insure.ai.modules.chat.entity.ChatMessage;
import com.insure.ai.modules.chat.entity.ChatSession;
import com.insure.ai.modules.chat.mapper.ChatMessageMapper;
import com.insure.ai.modules.chat.mapper.ChatSessionMapper;
import com.insure.ai.modules.kb.entity.KbQuestion;
import com.insure.ai.modules.kb.service.KbQuestionService;
import com.insure.ai.modules.policy.service.PolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 智能客服会话服务
 * <p>
 * 对话链路(Spring AI Alibaba / Spring AI ChatClient):
 * <ol>
 *     <li>知识库关键词召回,拼进 system 提示词</li>
 *     <li>按数据库中的默认模型配置构建 {@link ChatClient}(配置可在后台改,无需重启)</li>
 *     <li>挂载 {@link InsuranceQueryTools},模型可自行决定是否调用保单/理赔查询</li>
 *     <li>回复与 token 消耗落库</li>
 * </ol>
 *
 * @author insurance-ai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 默认召回条数 */
    private static final int RECALL_LIMIT = 3;
    /** 会话摘要最大长度 */
    private static final int SUMMARY_MAX_LENGTH = 200;

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final KbQuestionService kbQuestionService;
    private final SystemPromptBuilder systemPromptBuilder;
    private final DeepSeekProperties deepSeekProperties;
    private final AiModelFactory aiModelFactory;
    private final PolicyService policyService;

    /**
     * 查询当前用户的历史会话(按最后消息时间倒序)
     */
    public List<ChatSessionVO> listSessions(Long cusUserId) {
        List<ChatSession> sessions = chatSessionMapper.selectList(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getCusUserId, cusUserId)
                .orderByDesc(ChatSession::getLastMessageTime)
                .orderByDesc(ChatSession::getId));
        return sessions.stream().map(ChatSessionVO::from).toList();
    }

    /**
     * 查询某个会话的消息列表
     */
    public List<ChatMessageVO> listMessages(Long cusUserId, Long sessionId) {
        ChatSession session = requireOwnSession(cusUserId, sessionId);
        List<ChatMessage> messages = chatMessageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getSessionId, session.getId())
                .orderByAsc(ChatMessage::getId));
        return messages.stream().map(ChatMessageVO::from).toList();
    }

    /**
     * 新建会话(空会话,用户可先建后问)
     */
    public ChatSessionVO createSession(Long cusUserId, String title) {
        ChatSession session = new ChatSession();
        session.setCusUserId(cusUserId);
        session.setTitle(StringUtils.hasText(title) ? truncate(title, Constants.SESSION_TITLE_MAX_LENGTH) : "新会话");
        session.setMessageCount(0);
        session.setLastMessage("暂无消息");
        session.setLastMessageTime(LocalDateTime.now());
        chatSessionMapper.insert(session);
        return ChatSessionVO.from(session);
    }

    /**
     * 删除会话(逻辑删除,同时删除会话下的消息)
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long cusUserId, Long sessionId) {
        requireOwnSession(cusUserId, sessionId);
        chatSessionMapper.deleteById(sessionId);
        chatMessageMapper.delete(Wrappers.<ChatMessage>lambdaQuery().eq(ChatMessage::getSessionId, sessionId));
    }

    /**
     * 重命名会话
     */
    public void renameSession(Long cusUserId, Long sessionId, String title) {
        ChatSession session = requireOwnSession(cusUserId, sessionId);
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "会话标题不能为空");
        }
        ChatSession update = new ChatSession();
        update.setId(session.getId());
        update.setTitle(truncate(title.trim(), Constants.SESSION_TITLE_MAX_LENGTH));
        chatSessionMapper.updateById(update);
    }

    /**
     * 发送消息并获取 AI 回复
     * <p>注意:该方法不整体加事务,避免调用大模型期间长时间占用数据库连接</p>
     */
    public ChatAnswerVO send(Long cusUserId, ChatSendDTO dto) {
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        validateContent(content);
        // 1. 定位或新建会话
        ChatSession session = resolveSession(cusUserId, dto.getSessionId(), content);
        // 2. 用户消息先落库,保证即使模型调用失败也有历史记录
        saveMessage(session.getId(), cusUserId, ChatRoleEnum.USER, content, null, 0);
        // 3. 知识库召回 + 组装上下文
        List<KbQuestion> references = kbQuestionService.recall(content, RECALL_LIMIT);
        // 4. 调用大模型(带保单/理赔查询工具)
        AiModelConfig config = aiModelFactory.defaultConfig();
        ChatResponse response = callModel(config, cusUserId, session.getId(), references);
        String answer = extractText(response);
        // 5. AI 回复落库并更新会话摘要
        ChatMessage assistantMessage = saveMessage(session.getId(), cusUserId, ChatRoleEnum.ASSISTANT,
                answer, resolveModelName(response, config), extractTokens(response));
        updateSessionSummary(session, content, answer);
        // 6. 知识库命中次数 +1
        references.forEach(item -> kbQuestionService.increaseHitCount(item.getId()));

        ChatAnswerVO result = new ChatAnswerVO();
        result.setSessionId(session.getId());
        result.setAnswer(ChatMessageVO.from(assistantMessage));
        result.setReferences(references.stream().map(KbQuestion::getQuestion).toList());
        result.setCategory(InsuranceCategoryEnum.of(references.isEmpty()
                ? InsuranceCategoryEnum.OTHER.getCode() : references.get(0).getCategory()));
        return result;
    }

    /**
     * 调用大模型
     * <p>
     * 工具对象每次请求新建:{@code cusUserId} 从登录态传入并固化在工具实例中,
     * 模型无法通过参数伪造身份查询他人保单。
     * </p>
     */
    private ChatResponse callModel(AiModelConfig config, Long cusUserId, Long sessionId,
                                   List<KbQuestion> references) {
        long start = System.currentTimeMillis();
        try {
            ChatClient chatClient = ChatClient.builder(aiModelFactory.toModel(config)).build();
            ChatResponse response = chatClient.prompt()
                    .messages(buildMessages(sessionId, references))
                    .tools(new InsuranceQueryTools(policyService, cusUserId))
                    .call()
                    .chatResponse();
            log.info("[智能客服] 模型调用完成 config={} model={} 耗时={}ms",
                    config.getConfigName(), config.getModel(), System.currentTimeMillis() - start);
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[智能客服] 模型调用失败 config={} model={} 耗时={}ms",
                    config.getConfigName(), config.getModel(), System.currentTimeMillis() - start, e);
            throw new BusinessException(ResultCode.AI_CALL_FAILED);
        }
    }

    /**
     * 提取回复正文
     */
    private String extractText(ChatResponse response) {
        String text = response == null || response.getResult() == null
                || response.getResult().getOutput() == null
                ? null : response.getResult().getOutput().getText();
        if (!StringUtils.hasText(text)) {
            log.warn("[智能客服] 模型返回空内容");
            return "抱歉,本次没有生成有效回复,请重新描述您的问题,或联系人工客服。";
        }
        return text.trim();
    }

    /**
     * 实际使用的模型名(部分厂商不回传,回落到配置值)
     */
    private String resolveModelName(ChatResponse response, AiModelConfig config) {
        String model = response == null || response.getMetadata() == null
                ? null : response.getMetadata().getModel();
        return StringUtils.hasText(model) ? model : config.getModel();
    }

    /**
     * token 消耗
     */
    private Integer extractTokens(ChatResponse response) {
        Usage usage = response == null || response.getMetadata() == null
                ? null : response.getMetadata().getUsage();
        return usage == null || usage.getTotalTokens() == null ? 0 : usage.getTotalTokens();
    }

    /**
     * 参数校验
     */
    private void validateContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ResultCode.CHAT_CONTENT_EMPTY);
        }
        if (content.length() > Constants.QUESTION_MAX_LENGTH) {
            throw new BusinessException(ResultCode.CHAT_CONTENT_TOO_LONG);
        }
    }

    /**
     * 定位会话:sessionId 为空则新建,否则校验归属
     */
    private ChatSession resolveSession(Long cusUserId, Long sessionId, String content) {
        if (sessionId == null) {
            ChatSession session = new ChatSession();
            session.setCusUserId(cusUserId);
            session.setTitle(truncate(content, Constants.SESSION_TITLE_MAX_LENGTH));
            session.setMessageCount(0);
            session.setLastMessage(truncate(content, SUMMARY_MAX_LENGTH));
            session.setLastMessageTime(LocalDateTime.now());
            chatSessionMapper.insert(session);
            return session;
        }
        return requireOwnSession(cusUserId, sessionId);
    }

    /**
     * 校验会话归属,防止越权访问他人会话
     */
    private ChatSession requireOwnSession(Long cusUserId, Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.CHAT_SESSION_NOT_EXIST);
        }
        if (!session.getCusUserId().equals(cusUserId)) {
            throw new BusinessException(ResultCode.CHAT_SESSION_NO_PERMISSION);
        }
        return session;
    }

    /**
     * 组装上下文:System 提示词 + 最近 N 条历史(已含本轮用户消息)
     */
    private List<Message> buildMessages(Long sessionId, List<KbQuestion> references) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPromptBuilder.build(references)));

        int historySize = deepSeekProperties.getHistorySize() == null ? 10 : deepSeekProperties.getHistorySize();
        List<ChatMessage> history = chatMessageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + historySize));
        Collections.reverse(history);
        for (ChatMessage item : history) {
            if (!StringUtils.hasText(item.getContent())) {
                continue;
            }
            ChatRoleEnum role = ChatRoleEnum.of(item.getRole());
            messages.add(switch (role) {
                case USER -> new UserMessage(item.getContent());
                case ASSISTANT -> new AssistantMessage(item.getContent());
                case SYSTEM -> new SystemMessage(item.getContent());
            });
        }
        log.debug("[智能客服] 上下文消息数={}", messages.size());
        return messages;
    }

    /**
     * 消息落库
     */
    private ChatMessage saveMessage(Long sessionId, Long cusUserId, ChatRoleEnum role,
                                    String content, String model, Integer tokens) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setCusUserId(cusUserId);
        message.setRole(role.getCode());
        message.setContent(content);
        message.setModel(model);
        message.setTokens(tokens == null ? 0 : tokens);
        chatMessageMapper.insert(message);
        return message;
    }

    /**
     * 更新会话摘要信息
     */
    private void updateSessionSummary(ChatSession session, String question, String answer) {
        ChatSession update = new ChatSession();
        update.setId(session.getId());
        update.setLastMessage(truncate(answer, SUMMARY_MAX_LENGTH));
        update.setLastMessageTime(LocalDateTime.now());
        update.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + 2);
        if (!StringUtils.hasText(session.getTitle()) || "新会话".equals(session.getTitle())) {
            update.setTitle(truncate(question, Constants.SESSION_TITLE_MAX_LENGTH));
        }
        chatSessionMapper.updateById(update);
        // 同步回内存对象,便于返回给前端
        session.setTitle(update.getTitle() == null ? session.getTitle() : update.getTitle());
        session.setMessageCount(update.getMessageCount());
        session.setLastMessageTime(update.getLastMessageTime());
    }

    /**
     * 字符串截断
     */
    private String truncate(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    /**
     * 后台统计:总消息数 / 今日会话数等
     */
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
}
