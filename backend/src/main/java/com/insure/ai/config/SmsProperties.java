package com.insure.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 短信通道配置
 * <p>
 * 目前仅实现"生成验证码 + Redis 存储 + 频控 + 校验"的完整闭环,
 * <b>运营商实际发送动作尚未接入</b>(见 SmsServiceImpl#doSend 中的 TODO),
 * 因此 {@code mock=true} 时会把验证码回显给前端,便于演示与联调;
 * 正式上线前必须接入运营商并把 mock 置为 false。
 * </p>
 *
 * @author shaoyong.wang
 */
@Data
@Component
@ConfigurationProperties(prefix = "insure.sms")
public class SmsProperties {

    /** 是否模拟发送(不调用运营商,仅打日志并回显验证码) */
    private boolean mock = true;

    /** 注册是否强制校验短信验证码 */
    private boolean verifyRegister = true;

    /** 验证码有效期(秒) */
    private int expireSeconds = 300;

    /** 同一手机号同一场景的重发间隔(秒) */
    private int resendInterval = 60;

    /** 同一手机号每日发送上限 */
    private int dailyLimit = 10;

    /** 单条验证码允许的最大校验失败次数,超过即作废 */
    private int maxVerifyAttempts = 5;

    /** 验证码长度 */
    private int codeLength = 6;

    /** 短信签名,接入运营商时使用 */
    private String signName = "安鑫保险";

    /** 短信模板编号,接入运营商时使用 */
    private String templateCode = "SMS_000000";
}
