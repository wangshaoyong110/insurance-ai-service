package com.insure.ai.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流
 * <p>
 * 基于 Redis 计数器实现固定窗口限流，粒度是「当前登录用户 + 方法」，
 * 未登录时降级为「IP + 方法」。用于保护会调用大模型等按量计费/耗时接口。
 * </p>
 * <p>示例：{@code @RateLimit(limit = 10, seconds = 60)} 表示同一用户 60 秒内最多 10 次。</p>
 *
 * @author insurance-ai
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 窗口内允许的最大请求次数 */
    int limit() default 10;

    /** 窗口长度(秒) */
    int seconds() default 60;

    /**
     * 计数 key，默认取方法名
     * <p>同一业务存在多个入口(如同步 /chat/send 与流式 /chat/send/stream)时，
     * 配置相同的 key 可共享同一份计数，避免切换入口绕过限制。</p>
     */
    String key() default "";

    /** 超限提示语，为空时使用默认文案 */
    String message() default "";
}
