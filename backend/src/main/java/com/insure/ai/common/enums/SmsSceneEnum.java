package com.insure.ai.common.enums;

import lombok.Getter;

import java.util.Optional;

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
     * 严格解析场景标识,非法值返回空
     * <p>不要静默回落到注册场景:否则攻击者可用任意 scene 变换 Redis key,
     * 绕过「同一场景 60 秒重发」限制。空值仍按注册处理,兼容未传该字段的老客户端。</p>
     *
     * @param code 场景标识
     * @return 解析结果
     */
    public static Optional<SmsSceneEnum> parse(String code) {
        if (code == null || code.isBlank()) {
            return Optional.of(REGISTER);
        }
        for (SmsSceneEnum scene : values()) {
            if (scene.code.equalsIgnoreCase(code.trim())) {
                return Optional.of(scene);
            }
        }
        return Optional.empty();
    }
}
