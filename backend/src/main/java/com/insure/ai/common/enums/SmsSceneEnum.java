package com.insure.ai.common.enums;

import lombok.Getter;

/**
 * 短信验证码使用场景
 * <p>不同场景的验证码分开存储,避免注册验证码被拿去重置密码</p>
 *
 * @author shaoyong.wang
 */
@Getter
public enum SmsSceneEnum {

    /** 客户注册 */
    REGISTER("register", "注册"),
    /** 登录(短信验证码登录,预留) */
    LOGIN("login", "登录"),
    /** 重置密码 */
    RESET_PASSWORD("reset", "重置密码");

    /** 场景标识,参与 Redis key 与前端传参 */
    private final String code;
    /** 中文描述 */
    private final String label;

    SmsSceneEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 按标识解析场景,未匹配时默认注册场景
     *
     * @param code 场景标识
     * @return 场景枚举
     */
    public static SmsSceneEnum of(String code) {
        if (code != null) {
            for (SmsSceneEnum scene : values()) {
                if (scene.code.equalsIgnoreCase(code)) {
                    return scene;
                }
            }
        }
        return REGISTER;
    }
}
