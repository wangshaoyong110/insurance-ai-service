package com.insure.ai.modules.chat.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.insure.ai.common.enums.ChatMessageTypeEnum;
import com.insure.ai.common.enums.ChatRoleEnum;
import com.insure.ai.config.DeepSeekProperties;
import com.insure.ai.modules.chat.entity.ChatMessage;
import com.insure.ai.modules.chat.mapper.ChatMessageMapper;
import com.insure.ai.modules.chat.service.SystemPromptBuilder;
import com.insure.ai.modules.kb.entity.KbQuestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 对话上下文组装
 * <p>
 * 只负责把「System 提示词 + 滑动窗口历史 + 更早对话要点」拼成模型入参,
 * 不参与模型调用,也不写库。抽出来之后,发送服务只剩"调模型 + 推流"两件事。
 * </p>
 *
 * @author insurance-ai
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatContextBuilder {

    /** 早期对话要点最多回顾几条客户提问 */
    private static final int DIGEST_QUESTION_LIMIT = 8;
    /** 早期对话要点中单条提问的最大长度 */
    private static final int DIGEST_ITEM_LENGTH = 40;

    private final ChatMessageMapper chatMessageMapper;
    private final SystemPromptBuilder systemPromptBuilder;
    private final DeepSeekProperties deepSeekProperties;

    /**
     * 组装上下文:System 提示词 + 最近 N 条历史(已含本轮用户消息)
     */
    public List<Message> build(Long sessionId, List<KbQuestion> references) {
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
            // 图片/视频消息可能没有文字,给模型一个占位描述,否则整段上下文会缺一环
            String text = item.getContent();
            if (!StringUtils.hasText(text)) {
                ChatMessageTypeEnum type = ChatMessageTypeEnum.of(item.getMsgType());
                if (!type.isMedia()) {
                    continue;
                }
                text = "[" + type.getDesc() + "]";
            }
            ChatRoleEnum role = ChatRoleEnum.of(item.getRole());
            String finalText = text;
            messages.add(switch (role) {
                case USER -> new UserMessage(finalText);
                // 人工客服说过的内容对模型而言等同于助手历史回复,否则交回 AI 后会"忘记"人工已答复过什么
                case ASSISTANT, STAFF -> new AssistantMessage(finalText);
                case SYSTEM -> new SystemMessage(finalText);
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
                .map(item -> ChatTextUtils.truncate(item.getContent(), DIGEST_ITEM_LENGTH))
                .filter(StringUtils::hasText)
                .toList();
        if (questions.isEmpty()) {
            return;
        }
        messages.add(new SystemMessage("【更早的沟通要点】客户在本次会话此前还问过:"
                + String.join(";", questions)
                + "。若本轮问题与之相关,请结合这些背景回答,不要重复追问已知信息。"));
    }
}
