package com.insure.ai.common.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 人工工单状态枚举
 * <p>对应 svc_ticket.status</p>
 *
 * @author insurance-ai
 */
@Getter
public enum TicketStatusEnum {

    /** 待处理 */
    PENDING(0, "待处理"),
    /** 处理中 */
    PROCESSING(1, "处理中"),
    /** 已完成 */
    FINISHED(2, "已完成"),
    /** 已取消 */
    CANCELED(3, "已取消");

    private final Integer code;
    private final String desc;

    TicketStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static TicketStatusEnum of(Integer code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElse(PENDING);
    }

    public static String descOf(Integer code) {
        return code == null ? "未知" : of(code).getDesc();
    }

    /**
     * 是否已结束(结束后不允许再流转)
     */
    public boolean isClosed() {
        return this == FINISHED || this == CANCELED;
    }
}
