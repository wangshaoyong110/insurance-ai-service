package com.insure.ai.common.enums;

import lombok.Getter;

/**
 * 会话角色枚举(与 DeepSeek 接口的 role 字段保持一致)
 *
 * @author insurance-ai
 */
@Getter
public enum ChatRoleEnum {

    /** 用户提问 */
    USER("user", "用户"),
    /** AI 回复 */
    ASSISTANT("assistant", "智能客服"),
    /** 系统提示词 */
    SYSTEM("system", "系统");

    private final String code;
    private final String desc;

    ChatRoleEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按 code 解析,未知值按用户消息处理
     */
    public static ChatRoleEnum of(String code) {
        return java.util.Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(USER);
    }
}
