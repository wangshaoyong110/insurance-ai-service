package com.insure.ai.common.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 理赔状态枚举
 * <p>对应 ins_claim.status</p>
 *
 * @author insurance-ai
 */
@Getter
public enum ClaimStatusEnum {

    /** 已报案 */
    REPORTED(1, "已报案"),
    /** 资料审核 */
    REVIEWING(2, "资料审核"),
    /** 调查中 */
    INVESTIGATING(3, "调查中"),
    /** 审核通过 */
    APPROVED(4, "审核通过"),
    /** 已赔付 */
    PAID(5, "已赔付"),
    /** 拒赔 */
    REJECTED(6, "拒赔"),
    /** 已撤销 */
    CANCELED(7, "已撤销");

    private final Integer code;
    private final String desc;

    ClaimStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ClaimStatusEnum of(Integer code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElse(REPORTED);
    }

    public static String descOf(Integer code) {
        return code == null ? "未知" : of(code).getDesc();
    }

    /**
     * 是否已结案(结案后不再展示"预计赔付日期")
     */
    public static boolean isClosed(Integer code) {
        return PAID.code.equals(code) || REJECTED.code.equals(code) || CANCELED.code.equals(code);
    }
}
