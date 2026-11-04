package com.insure.ai.common.enums;

import lombok.Getter;

/**
 * 通用状态枚举
 *
 * @author insurance-ai
 */
@Getter
public enum CommonStatusEnum {

    /** 启用 */
    ENABLE(1, "启用"),
    /** 禁用 */
    DISABLE(0, "禁用");

    private final Integer code;
    private final String desc;

    CommonStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static String descOf(Integer code) {
        return ENABLE.getCode().equals(code) ? ENABLE.desc : DISABLE.desc;
    }
}
