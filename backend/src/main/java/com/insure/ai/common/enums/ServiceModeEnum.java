package com.insure.ai.common.enums;

import lombok.Getter;

/**
 * 会话服务模式
 * <p>
 * AI 与人工不能同时回答同一个会话，否则客户会收到两套互相矛盾的结论。
 * 用状态机把"谁在答"显式化：转人工后进入排队（AI 仍可答），
 * 坐席接单后切到人工（AI 静默），结单/取消后再交回 AI。
 * </p>
 *
 * @author insurance-ai
 */
@Getter
public enum ServiceModeEnum {

    /** AI 自助回答 */
    AI(0, "AI 自助"),
    /** 已提交工单、等待坐席接入；此阶段 AI 继续回答，避免客户干等 */
    WAITING(1, "等待人工"),
    /** 坐席已接入，AI 静默，由人工回复 */
    HUMAN(2, "人工接入中");

    private final Integer code;
    private final String desc;

    ServiceModeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按 code 解析,未知值按 AI 自助处理
     */
    public static ServiceModeEnum of(Integer code) {
        if (code != null) {
            for (ServiceModeEnum item : values()) {
                if (item.code.equals(code)) {
                    return item;
                }
            }
        }
        return AI;
    }

    /**
     * 是否处于人工阶段(AI 必须静默)
     */
    public boolean isHuman() {
        return this == HUMAN;
    }
}
