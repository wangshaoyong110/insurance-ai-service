package com.insure.ai.common.enums;

import lombok.Getter;

/**
 * 用户类型枚举
 *
 * @author insurance-ai
 */
@Getter
public enum UserTypeEnum {

    /** 超级管理员(可登录后台管理) */
    ADMIN("ADMIN", "超级管理员"),
    /** 普通客户(仅可使用智能客服) */
    CUSTOMER("CUSTOMER", "普通客户");

    private final String code;
    private final String desc;

    UserTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static UserTypeEnum of(String code) {
        for (UserTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return CUSTOMER;
    }

    public static String descOf(String code) {
        return of(code).getDesc();
    }
}
