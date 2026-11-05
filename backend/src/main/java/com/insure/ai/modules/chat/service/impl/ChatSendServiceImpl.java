package com.insure.ai.modules.chat.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.Constants;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.ChatRoleEnum;
import com.insure.ai.common.enums.InsuranceCategoryEnum;
import com.insure.ai.common.enums.ServiceModeEnum;
import com.insure.ai.common.util.RealNameGate;
import com.insure.ai.modules.ai.entity.AiModelConfig;
import com.insure.ai.modules.ai.service.AiModelFactory;
import com.insure.ai.modules.ai.tool.InsuranceQueryTools;
import com.insure.ai.modules.chat.dto.ChatAnswerVO;
import com.insure.ai.modules.chat.dto.ChatMessageVO;
import com.insure.ai.modules.chat.dto.ChatSendDTO;
import com.insure.ai.modules.chat.entity.ChatMessage;
import com.insure.ai.modules.chat.entity.ChatSession;
import com.insure.ai.modules.chat.service.ChatSendService;
import com.insure.ai.modules.chat.service.ChatSessionService;
import com.insure.ai.modules.chat.support.ChatContextBuilder;
import com.insure.ai.modules.chat.support.RealNameVerifier;
import com.insure.ai.modules.kb.dto.RecallResult;
import com.insure.ai.modules.kb.entity.KbQuestion;
import com.insure.ai.modules.kb.service.GapAnalysisService;
import com.insure.ai.modules.kb.service.KbQuestionService;
import com.insure.ai.modules.policy.service.PolicyService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 问答链路实现
 *
 * @author insurance-ai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSendServiceImpl implements ChatSendService {

    /** 默认召回条数 */
    private static final int RECALL_LIMIT = 3;
    /** 流式连接最长保持时间(毫秒),需大于模型自身超时 */
    private static final long STREAM_TIMEOUT_MS = 300_000L;

    private final ChatSessionService chatSessionService;
    private final ChatContextBuilder chatContextBuilder;
    private final RealNameVerifier realNameVerifier;
    private final KbQuestionService kbQuestionService;
    private final GapAnalysisService gapAnalysisService;
    private final AiModelFactory aiModelFactory;
    private final PolicyService policyService;
    private final ObjectMapper objectMapper;

    /** 流式对话专用线程池(复用全局 taskExecutor,已带 traceId 传递) */
    @Resource(name = "taskExecutor")
    private Executor chatExecutor;

    @Override
    public ChatAnswerVO send(Long cusUserId, ChatSendDTO dto) {
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        validateContent(content);
        // 1. 定位或新建会话
        ChatSession session = chatSessionService.resolveSession(cusUserId, dto.getSessionId(), content);
        // 2. 用户消息先落库,保证即使模型调用失败也有历史记录
        chatSessionService.saveMessage(session.getId(), cusUserId, ChatRoleEnum.USER, content, null, 0);
        // 2.1 人工已接管:消息照常落库(坐席能看到),但不能再让 AI 抢答
        if (ServiceModeEnum.of(session.getServiceMode()).isHuman()) {
            return humanReply(session, content);
        }
        // 3. 未实名客户问及本人业务数据:直接给引导话术,不调模型(省 token 且口径统一)
        boolean verified = realNameVerifier.isVerified(cusUserId);
        if (!verified && RealNameGate.requiresRealName(content)) {
            return realNameBlocked(session, cusUserId, content);
        }
        // 4. 知识库召回 + 组装上下文
        RecallResult recall = kbQuestionService.recallWithScore(content, RECALL_LIMIT);
        List<KbQuestion> references = recall.items();
        // 5. 调用大模型(带保单/理赔查询工具;未实名时工具内部拒绝查询,拿不到任何业务数据)
        AiModelConfig config = aiModelFactory.defaultConfig();
        InsuranceQueryTools tools = new InsuranceQueryTools(policyService, cusUserId, verified);
        ChatResponse response = callModel(config, tools, session.getId(), references);
        String answer = extractText(response);
        // 6. AI 回复落库并更新会话摘要
        ChatMessage assistantMessage = chatSessionService.saveMessage(session.getId(), cusUserId,
                ChatRoleEnum.ASSISTANT, answer, resolveModelName(response, config), extractTokens(response));
        chatSessionService.updateSessionSummary(session, content, answer);
        // 7. 只有"该查知识库却没查到"才算盲区；走工具拿到真实业务数据的提问不算
        recordGapIfWeak(cusUserId, session.getId(), content, recall.bestScore(), tools.isInvoked());
        // 8. 知识库命中次数 +1
        references.forEach(item -> kbQuestionService.increaseHitCount(item.getId()));

        ChatAnswerVO result = new ChatAnswerVO();
        result.setSessionId(session.getId());
        result.setAnswer(ChatMessageVO.from(assistantMessage));
        result.setReferences(references.stream().map(KbQuestion::getQuestion).toList());
        result.setCategory(InsuranceCategoryEnum.of(references.isEmpty()
                ? InsuranceCategoryEnum.OTHER.getCode() : references.get(0).getCategory()));
        return result;
    }

    @Override
    public SseEmitter sendStream(Long cusUserId, ChatSendDTO dto) {
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        validateContent(content);
        ChatSession session = chatSessionService.resolveSession(cusUserId, dto.getSessionId(), content);
        chatSessionService.saveMessage(session.getId(), cusUserId, ChatRoleEnum.USER, content, null, 0);
        // 人工已接管时直接回一个空回复事件：前端收到 serviceMode=2 便不再等待 AI,改为轮询坐席消息
        if (ServiceModeEnum.of(session.getServiceMode()).isHuman()) {
            return humanSilentStream(session, content);
        }
        // 未实名客户问及本人业务数据:同步落库引导话术,再用事件流回放,前端同样能拿到 needRealName
        boolean verified = realNameVerifier.isVerified(cusUserId);
        if (!verified && RealNameGate.requiresRealName(content)) {
            return realNameBlockedStream(session, cusUserId, content);
        }
        RecallResult recall = kbQuestionService.recallWithScore(content, RECALL_LIMIT);
        List<KbQuestion> references = recall.items();
        AiModelConfig config = aiModelFactory.defaultConfig();
        // 工具实例要贯穿整轮对话：结束后用它判断本轮是否查过业务数据，再决定要不要记知识盲区
        InsuranceQueryTools tools = new InsuranceQueryTools(policyService, cusUserId, verified);

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        emitter.onTimeout(() -> log.warn("[智能客服] 流式连接超时 sessionId={}", session.getId()));
        emitter.onError(e -> log.warn("[智能客服] 流式连接异常 sessionId={} 原因={}", session.getId(), e.getMessage()));
        chatExecutor.execute(() -> streamToEmitter(emitter, config, cusUserId, session, content, references,
                tools, recall.bestScore()));
        return emitter;
    }

    /* ==================================================================
     * 非模型回复的三种短路分支
     * ================================================================== */

    /**
     * 人工接管中:只更新末条消息,不回 AI 内容
     */
    private ChatAnswerVO humanReply(ChatSession session, String content) {
        chatSessionService.touchSession(session.getId(), content);
        ChatAnswerVO human = new ChatAnswerVO();
        human.setSessionId(session.getId());
        human.setServiceMode(session.getServiceMode());
        return human;
    }

    /**
     * 未实名拦截:落一条引导话术并标记 needRealName
     */
    private ChatAnswerVO realNameBlocked(ChatSession session, Long cusUserId, String content) {
        String answer = RealNameGate.guidance();
        ChatMessage assistantMessage = chatSessionService.saveMessage(session.getId(), cusUserId,
                ChatRoleEnum.ASSISTANT, answer, null, 0);
        chatSessionService.updateSessionSummary(session, content, answer);
        ChatAnswerVO blocked = new ChatAnswerVO();
        blocked.setSessionId(session.getId());
        blocked.setAnswer(ChatMessageVO.from(assistantMessage));
        blocked.setReferences(List.of());
        blocked.setCategory(InsuranceCategoryEnum.OTHER);
        blocked.setNeedRealName(true);
        return blocked;
    }

    /**
     * 人工接管中的流式形态:推 meta + done,前端据此转入轮询
     */
    private SseEmitter humanSilentStream(ChatSession session, String content) {
        chatSessionService.touchSession(session.getId(), content);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        chatExecutor.execute(() -> {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("sessionId", session.getId());
            meta.put("serviceMode", session.getServiceMode());
            meta.put("references", List.of());
            sendEvent(emitter, "meta", meta);
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("sessionId", session.getId());
            done.put("serviceMode", session.getServiceMode());
            sendEvent(emitter, "done", done);
            emitter.complete();
        });
        return emitter;
    }

    /**
     * 未实名拦截的流式形态:把已落库的引导话术当作整段 delta 推回去
     */
    private SseEmitter realNameBlockedStream(ChatSession session, Long cusUserId, String content) {
        String blockedAnswer = RealNameGate.guidance();
        ChatMessage blockedMessage = chatSessionService.saveMessage(session.getId(), cusUserId,
                ChatRoleEnum.ASSISTANT, blockedAnswer, null, 0);
        chatSessionService.updateSessionSummary(session, content, blockedAnswer);
        SseEmitter blockedEmitter = new SseEmitter(STREAM_TIMEOUT_MS);
        chatExecutor.execute(() -> {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("sessionId", session.getId());
            meta.put("references", List.of());
            meta.put("category", InsuranceCategoryEnum.OTHER.name());
            meta.put("needRealName", true);
            sendEvent(blockedEmitter, "meta", meta);
            sendDelta(blockedEmitter, blockedAnswer);
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("sessionId", session.getId());
            done.put("messageId", blockedMessage.getId());
            done.put("needRealName", true);
            sendEvent(blockedEmitter, "done", done);
            blockedEmitter.complete();
        });
        return blockedEmitter;
    }

    /* ==================================================================
     * 模型调用
     * ================================================================== */

    /**
     * 真正执行流式调用并把增量推给客户端
     */
    private void streamToEmitter(SseEmitter emitter, AiModelConfig config, Long cusUserId,
                                 ChatSession session, String content, List<KbQuestion> references,
                                 InsuranceQueryTools tools, int bestScore) {
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
                    .messages(chatContextBuilder.build(session.getId(), references))
                    .tools(tools)
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
            ChatMessage assistantMessage = chatSessionService.saveMessage(session.getId(), cusUserId,
                    ChatRoleEnum.ASSISTANT, answer, modelName[0], totalTokens[0]);
            chatSessionService.updateSessionSummary(session, content, answer);
            // 走工具拿到真实业务数据的提问不算知识盲区，只有"该查知识库却没查到"才留痕
            recordGapIfWeak(cusUserId, session.getId(), content, bestScore, tools.isInvoked());
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
     * 调用大模型
     * <p>
     * 工具对象每次请求新建:{@code cusUserId} 从登录态传入并固化在工具实例中,
     * 模型无法通过参数伪造身份查询他人保单。
     * </p>
     */
    private ChatResponse callModel(AiModelConfig config, InsuranceQueryTools tools,
                                   Long sessionId, List<KbQuestion> references) {
        long start = System.currentTimeMillis();
        try {
            ChatClient chatClient = ChatClient.builder(aiModelFactory.toModel(config)).build();
            ChatResponse response = chatClient.prompt()
                    .messages(chatContextBuilder.build(sessionId, references))
                    .tools(tools)
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

    /* ==================================================================
     * SSE 推送与后续处理
     * ================================================================== */

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
     * 知识库没覆盖到时记录一条盲区
     * <p>
     * 判定标准是召回最高分低于关键词权重:说明只命中了零散的二字片段，
     * 知识库里并没有真正对应这个问题的标准问答。这类问题正是运营该补的。
     * </p>
     */
    private void recordGapIfWeak(Long cusUserId, Long sessionId, String content, int bestScore, boolean toolUsed) {
        if (toolUsed) {
            return;
        }
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
}
