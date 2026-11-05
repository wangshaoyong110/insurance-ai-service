package com.insure.ai.common.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 差评原因枚举
 * <p>对应 chat_feedback.reason</p>
 *
 * @author insurance-ai
 */
@Getter
public enum FeedbackReasonEnum {

    /** 信息有误 */
    WRONG("WRONG", "信息有误"),
    /** 答非所问 */
    IRRELEVANT("IRRELEVANT", "答非所问"),
    /** 不够完整 */
    INCOMPLETE("INCOMPLETE", "不够完整"),
    /** 响应太慢 */
    SLOW("SLOW", "响应太慢"),
    /** 其他 */
    OTHER("OTHER", "其他");

    private final String code;
    private final String desc;

    FeedbackReasonEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static FeedbackReasonEnum of(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElse(OTHER);
    }

    public static String descOf(String code) {
        return of(code).getDesc();
    }
}
