package com.insure.ai.modules.chat.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.Constants;
import com.insure.ai.common.PageResult;
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
import com.insure.ai.modules.kb.service.GapAnalysisService;
import com.insure.ai.modules.kb.service.KbQuestionService;
import com.insure.ai.modules.policy.service.PolicyService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

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
    /** 流式连接最长保持时间(毫秒),需大于模型自身超时 */
    private static final long STREAM_TIMEOUT_MS = 300_000L;
    /** 消息分页默认条数 */
    private static final long DEFAULT_MESSAGE_PAGE_SIZE = 50L;
    /** 消息分页上限 */
    private static final long MAX_MESSAGE_PAGE_SIZE = 200L;
    /** 早期对话要点最多回顾几条客户提问 */
    private static final int DIGEST_QUESTION_LIMIT = 8;
    /** 早期对话要点中单条提问的最大长度 */
    private static final int DIGEST_ITEM_LENGTH = 40;

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final KbQuestionService kbQuestionService;
    private final GapAnalysisService gapAnalysisService;
    private final SystemPromptBuilder systemPromptBuilder;
    private final DeepSeekProperties deepSeekProperties;
    private final AiModelFactory aiModelFactory;
    private final PolicyService policyService;
    private final ObjectMapper objectMapper;

    /** 流式对话专用线程池(复用全局 taskExecutor,已带 traceId 传递) */
    @Resource(name = "taskExecutor")
    private Executor chatExecutor;

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
     * 查询某个会话的消息(从最新往回翻，返回结果内部按时间正序，便于直接渲染)
     * <p>默认每页 50 条：单个会话攒到几百条后一次性全量下发会明显拖慢首屏</p>
     */
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
        KbQuestionService.RecallResult recall = kbQuestionService.recallWithScore(content, RECALL_LIMIT);
        List<KbQuestion> references = recall.items();
        recordGapIfWeak(cusUserId, session.getId(), content, recall.bestScore());
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
     * 流式发送消息(SSE)
     * <p>
     * 与 {@link #send} 的差异只在传输形态:校验、建会话、用户消息落库仍在请求线程内同步完成
     * (失败可正常返回 JSON 错误码),模型生成阶段放进线程池逐段推送,结束后统一落库。
     * </p>
     * <p>事件协议:{@code meta}(会话与参考来源) → {@code delta}(增量文本,可多次) → {@code done} 或 {@code error}。</p>
     */
    public SseEmitter sendStream(Long cusUserId, ChatSendDTO dto) {
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        validateContent(content);
        ChatSession session = resolveSession(cusUserId, dto.getSessionId(), content);
        saveMessage(session.getId(), cusUserId, ChatRoleEnum.USER, content, null, 0);
        KbQuestionService.RecallResult recall = kbQuestionService.recallWithScore(content, RECALL_LIMIT);
        List<KbQuestion> references = recall.items();
        recordGapIfWeak(cusUserId, session.getId(), content, recall.bestScore());
        AiModelConfig config = aiModelFactory.defaultConfig();

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        emitter.onTimeout(() -> log.warn("[智能客服] 流式连接超时 sessionId={}", session.getId()));
        emitter.onError(e -> log.warn("[智能客服] 流式连接异常 sessionId={} 原因={}", session.getId(), e.getMessage()));
        chatExecutor.execute(() -> streamToEmitter(emitter, config, cusUserId, session, content, references));
        return emitter;
    }

    /**
     * 真正执行流式调用并把增量推给客户端
     */
    private void streamToEmitter(SseEmitter emitter, AiModelConfig config, Long cusUserId,
                                 ChatSession session, String content, List<KbQuestion> references) {
        long start = System.currentTimeMillis();
        StringBuilder buffer = new StringBuilder();
        String[] modelName = {config.getModel()};
        AtomicBoolean disconnected = new AtomicBoolean(false);
        // 流式响应的 usage 在最后一块才完整，逐块覆盖取到的最新值
        int[] totalTokens = {0};

        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("sessionId", session.getId());
            meta.put("references", references.stream().map(KbQuestion::getQuestion).toList());
            meta.put("category", InsuranceCategoryEnum.of(references.isEmpty()
                    ? InsuranceCategoryEnum.OTHER.getCode() : references.get(0).getCategory()).name());
            sendEvent(emitter, "meta", meta);

            ChatClient chatClient = ChatClient.builder(aiModelFactory.toModel(config)).build();
            // toIterable 会阻塞到上游结束，正好符合"异步线程里同步消费"的用法，且能同时拿到正文与用量
            Iterable<ChatResponse> responses = chatClient.prompt()
                    .messages(buildMessages(session.getId(), references))
                    .tools(new InsuranceQueryTools(policyService, cusUserId))
                    .stream()
                    .chatResponse()
                    .toIterable();

            for (ChatResponse response : responses) {
                if (disconnected.get()) {
                    break;
                }
                if (response == null) {
                    continue;
                }
                String text = response.getResult() == null || response.getResult().getOutput() == null
                        ? null : response.getResult().getOutput().getText();
                if (StringUtils.hasText(text)) {
                    buffer.append(text);
                    if (!sendDelta(emitter, text)) {
                        disconnected.set(true);
                        break;
                    }
                }
                if (response.getMetadata() != null) {
                    if (StringUtils.hasText(response.getMetadata().getModel())) {
                        modelName[0] = response.getMetadata().getModel();
                    }
                    Usage usage = response.getMetadata().getUsage();
                    if (usage != null && usage.getTotalTokens() != null && usage.getTotalTokens() > 0) {
                        totalTokens[0] = usage.getTotalTokens();
                    }
                }
            }

            String answer = buffer.length() == 0
                    ? "抱歉,本次没有生成有效回复,请重新描述您的问题,或联系人工客服。"
                    : buffer.toString().trim();

            // token 之前这里写死 0，导致后台"模型成本"统计对走流式的对话完全失真
            ChatMessage assistantMessage = saveMessage(session.getId(), cusUserId, ChatRoleEnum.ASSISTANT,
                    answer, modelName[0], totalTokens[0]);
            updateSessionSummary(session, content, answer);
            references.forEach(item -> kbQuestionService.increaseHitCount(item.getId()));

            Map<String, Object> done = new LinkedHashMap<>();
            done.put("sessionId", session.getId());
            done.put("messageId", assistantMessage.getId());
            done.put("model", modelName[0]);
            done.put("tokens", totalTokens[0]);
            done.put("createTime", LocalDateTime.now().toString());
            if (!disconnected.get()) {
                sendEvent(emitter, "done", done);
            }
            emitter.complete();
            log.info("[智能客服] 流式调用完成 sessionId={} 字数={} 耗时={}ms",
                    session.getId(), answer.length(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[智能客服] 流式调用失败 sessionId={} 耗时={}ms",
                    session.getId(), System.currentTimeMillis() - start, e);
            if (!disconnected.get()) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("message", ResultCode.AI_CALL_FAILED.getMessage());
                sendEvent(emitter, "error", error);
            }
            emitter.complete();
        }
    }

    /**
     * 推送增量文本,返回 false 表示客户端已断开
     * <p>
     * 正文用 JSON 包装而非裸文本：模型输出里的换行如果直接进 data 字段，
     * 会和 SSE 的"空行分帧"语义相互干扰，包一层转义后最稳。
     * </p>
     */
    private boolean sendDelta(SseEmitter emitter, String text) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("t", text));
            emitter.send(SseEmitter.event().name("delta").data(json, MediaType.TEXT_PLAIN));
            return true;
        } catch (IOException | IllegalStateException e) {
            log.info("[智能客服] 客户端已断开,停止推送: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 推送结构化事件(JSON 文本)
     */
    private void sendEvent(SseEmitter emitter, String name, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            emitter.send(SseEmitter.event().name(name).data(json, MediaType.TEXT_PLAIN));
        } catch (IOException | IllegalStateException e) {
            log.info("[智能客服] 事件推送失败 name={} 原因={}", name, e.getMessage());
        }
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
     * 知识库没覆盖到时记录一条盲区
     * <p>
     * 判定标准是召回最高分低于关键词权重:说明只命中了零散的二字片段，
     * 知识库里并没有真正对应这个问题的标准问答。这类问题正是运营该补的。
     * </p>
     */
    private void recordGapIfWeak(Long cusUserId, Long sessionId, String content, int bestScore) {
        if (bestScore < KbQuestionService.weakScoreThreshold()) {
            gapAnalysisService.record(cusUserId, sessionId, content, bestScore);
        }
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

        // 超出滑动窗口的早期内容不能直接丢：客户十几轮前提过的诉求，后面往往还要用
        appendEarlierDigest(messages, sessionId, history);

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
     * 追加早期对话要点
     * <p>
     * 上下文只带最近 N 条消息，长会话里更早的内容会被直接丢掉，表现为"聊了十几轮后 AI 突然失忆"。
     * 这里把窗口之外的客户提问抽出来压成一句要点，不额外调模型、不增加 token 费用，
     * 但足以让模型知道此前聊过哪些事。
     * </p>
     */
    private void appendEarlierDigest(List<Message> messages, Long sessionId, List<ChatMessage> history) {
        if (history.isEmpty()) {
            return;
        }
        Long oldestId = history.get(0).getId();
        List<ChatMessage> earlier = chatMessageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getSessionId, sessionId)
                .lt(ChatMessage::getId, oldestId)
                .eq(ChatMessage::getRole, ChatRoleEnum.USER.getCode())
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + DIGEST_QUESTION_LIMIT));
        if (earlier.isEmpty()) {
            return;
        }
        Collections.reverse(earlier);
        List<String> questions = earlier.stream()
                .map(item -> truncate(item.getContent(), DIGEST_ITEM_LENGTH))
                .filter(StringUtils::hasText)
                .toList();
        if (questions.isEmpty()) {
            return;
        }
        messages.add(new SystemMessage("【更早的沟通要点】客户在本次会话此前还问过:"
                + String.join(";", questions)
                + "。若本轮问题与之相关,请结合这些背景回答,不要重复追问已知信息。"));
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
