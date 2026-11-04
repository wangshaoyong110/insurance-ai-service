package com.insure.ai.common.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 保单状态枚举
 * <p>对应 ins_policy.status</p>
 *
 * @author insurance-ai
 */
@Getter
public enum PolicyStatusEnum {

    /** 有效 */
    EFFECTIVE(1, "有效"),
    /** 中止 */
    SUSPENDED(2, "中止"),
    /** 失效 */
    LAPSED(3, "失效"),
    /** 已退保 */
    SURRENDERED(4, "已退保"),
    /** 满期终止 */
    EXPIRED(5, "满期终止");

    private final Integer code;
    private final String desc;

    PolicyStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static PolicyStatusEnum of(Integer code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElse(LAPSED);
    }

    public static String descOf(Integer code) {
        return code == null ? "未知" : of(code).getDesc();
    }
}
