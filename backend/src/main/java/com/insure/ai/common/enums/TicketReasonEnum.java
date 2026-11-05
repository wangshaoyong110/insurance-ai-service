package com.insure.ai.common.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 转人工原因枚举
 * <p>对应 svc_ticket.reason，同时作为客户端「转人工」弹窗的选项来源</p>
 *
 * @author insurance-ai
 */
@Getter
public enum TicketReasonEnum {

    /** 回答不满意 */
    DISSATISFIED("DISSATISFIED", "回答不满意"),
    /** 投诉建议 */
    COMPLAINT("COMPLAINT", "投诉建议"),
    /** 业务太复杂 */
    COMPLEX("COMPLEX", "业务复杂需人工"),
    /** 其他 */
    OTHER("OTHER", "其他");

    private final String code;
    private final String desc;

    TicketReasonEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static TicketReasonEnum of(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElse(OTHER);
    }

    public static String descOf(String code) {
        return of(code).getDesc();
    }
}
