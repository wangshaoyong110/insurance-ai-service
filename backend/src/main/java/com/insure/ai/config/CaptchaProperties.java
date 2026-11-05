package com.insure.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 图形验证码配置
 * <p>验证码答案不落库、不写明文到 Redis,只保存 SM3 摘要,5 分钟自动过期。</p>
 *
 * @author shaoyong.wang
 */
@Data
@Component
@ConfigurationProperties(prefix = "insure.captcha")
public class CaptchaProperties {

    /** 是否启用校验(仅限本地联调可临时置 false,生产必须为 true) */
    private boolean enabled = true;

    /**
     * 演示模式:接口额外回显验证码明文,便于无图形界面的自动化联调。
     * 生产必须置 false,否则图形验证码形同虚设。
     */
    private boolean debug = true;

    /** 有效期(秒),与短信验证码保持一致 */
    private int expireSeconds = 300;

    /** 验证码字符数 */
    private int length = 4;

    /** 单张验证码允许的连续错误次数,超过即作废 */
    private int maxVerifyAttempts = 3;

    /** 图片宽度(像素) */
    private int width = 120;

    /** 图片高度(像素) */
    private int height = 40;
}
