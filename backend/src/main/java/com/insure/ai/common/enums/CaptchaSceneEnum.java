package com.insure.ai.common.enums;

import lombok.Getter;

import java.util.Optional;

/**
 * 图形验证码使用场景
 * <p>按场景隔离 Redis key,登录页拿到的验证码不能拿去注册场景使用,
 * 反过来也一样,避免一张图跨用途重放。</p>
 *
 * @author shaoyong.wang
 */
@Getter
public enum CaptchaSceneEnum {

    /** 登录(客户端与管理端共用) */
    LOGIN("login", "登录"),
    /** 注册(发短信前校验 + 注册提交复核) */
    REGISTER("register", "注册");

    private final String code;
    private final String label;

    CaptchaSceneEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 严格解析场景,非法值返回空而不是静默回落,防止用异常参数变换 Redis key
     *
     * @param code 场景标识
     * @return 场景枚举
     */
    public static Optional<CaptchaSceneEnum> parse(String code) {
        if (code != null) {
            for (CaptchaSceneEnum scene : values()) {
                if (scene.code.equalsIgnoreCase(code.trim())) {
                    return Optional.of(scene);
                }
            }
        }
        return Optional.empty();
    }
}
