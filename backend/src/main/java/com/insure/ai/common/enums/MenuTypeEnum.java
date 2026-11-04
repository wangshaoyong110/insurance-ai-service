package com.insure.ai.common.enums;

import lombok.Getter;

/**
 * 菜单类型枚举
 *
 * @author insurance-ai
 */
@Getter
public enum MenuTypeEnum {

    /** 目录:仅作为分组,前端渲染为可展开子菜单 */
    DIR("DIR", "目录"),
    /** 菜单:对应一个前端页面路由 */
    MENU("MENU", "菜单"),
    /** 按钮:仅作为权限点,不参与导航渲染 */
    BUTTON("BUTTON", "按钮");

    private final String code;
    private final String desc;

    MenuTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static MenuTypeEnum of(String code) {
        for (MenuTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return MENU;
    }

    public static String descOf(String code) {
        return of(code).getDesc();
    }
}
