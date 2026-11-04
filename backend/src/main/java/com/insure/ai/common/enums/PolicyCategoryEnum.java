package com.insure.ai.common.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 险种枚举
 * <p>对应 ins_policy.category,用于后台展示与 AI 工具的输出措辞</p>
 *
 * @author insurance-ai
 */
@Getter
public enum PolicyCategoryEnum {

    /** 重疾险 */
    CRITICAL("CRITICAL", "重疾险"),
    /** 医疗险 */
    MEDICAL("MEDICAL", "医疗险"),
    /** 意外险 */
    ACCIDENT("ACCIDENT", "意外险"),
    /** 寿险 */
    LIFE("LIFE", "寿险"),
    /** 年金险 */
    ANNUITY("ANNUITY", "年金险"),
    /** 其他 */
    OTHER("OTHER", "其他");

    private final String code;
    private final String desc;

    PolicyCategoryEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static PolicyCategoryEnum of(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(OTHER);
    }

    public static String descOf(String code) {
        return code == null ? OTHER.desc : of(code).getDesc();
    }
}
