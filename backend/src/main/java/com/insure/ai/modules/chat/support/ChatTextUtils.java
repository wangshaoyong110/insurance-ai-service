package com.insure.ai.modules.chat.support;

import com.insure.ai.common.enums.ChatMessageTypeEnum;
import com.insure.ai.modules.chat.entity.ChatMessage;
import org.springframework.util.StringUtils;

/**
 * 会话文本小工具
 * <p>截断与预览是会话模块里被反复复用的小逻辑,集中在此避免各服务各写一份</p>
 *
 * @author insurance-ai
 */
public final class ChatTextUtils {

    private ChatTextUtils() {
    }

    /**
     * 字符串截断(超长时追加省略号)
     */
    public static String truncate(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    /**
     * 会话摘要只存一行预览:图片/视频没有文字时给个占位,避免列表里空白
     */
    public static String previewOf(ChatMessage message) {
        if (StringUtils.hasText(message.getContent())) {
            return message.getContent();
        }
        ChatMessageTypeEnum type = ChatMessageTypeEnum.of(message.getMsgType());
        return "[" + type.getDesc() + "]";
    }
}
