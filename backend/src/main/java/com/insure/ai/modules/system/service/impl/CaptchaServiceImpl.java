package com.insure.ai.modules.system.service.impl;

import com.insure.ai.common.BusinessException;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.crypto.Sm3Utils;
import com.insure.ai.common.enums.CaptchaSceneEnum;
import com.insure.ai.config.CaptchaProperties;
import com.insure.ai.modules.system.dto.CaptchaVO;
import com.insure.ai.modules.system.service.CaptchaService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 图形验证码实现
 * <p>
 * 存储格式:{@code captcha:{scene}:{captchaId} -> SM3(答案)|是否已验证|绑定手机号}。
 * 答案只存国密 SM3 摘要,Redis 里不出现明文;每张验证码 5 分钟有效、按场景隔离、
 * 连续答错 3 次自动作废,登录与注册提交成功后立即删除,不可重放。
 * </p>
 * <p>
 * <b>注册链路的关键顺序</b>:发短信前先调 {@link #verifyForSms},校验通过才把状态置为
 * 「已验证 + 绑定手机号」;注册提交时 {@link #verifyForRegister} 要求这个状态存在且手机号
 * 一致,从而保证「没有通过图形验证码就不会有短信发出」。
 * </p>
 *
 * @author shaoyong.wang
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

    static {
        // 容器内通常没有 X11,强制 headless;BufferedImage + 逻辑字体在 headless 下可正常工作
        System.setProperty("java.awt.headless", "true");
    }

    /** 验证码 key 前缀 */
    private static final String KEY_PREFIX = "captcha:";
    /** 校验失败次数 key 前缀 */
    private static final String FAIL_PREFIX = "captcha:fail:";
    /** 去掉 0/O/1/I 等易混淆字符 */
    private static final char[] CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    /** 已验证状态 */
    private static final String VERIFIED = "1";
    /** 未验证状态 */
    private static final String UNVERIFIED = "0";
    /** 状态分隔字段数 */
    private static final int FIELD_COUNT = 3;
    /** 刷写已验证状态后至少保留的有效期(秒) */
    private static final long MIN_REMAIN_SECONDS = 60L;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final CaptchaProperties properties;

    @PostConstruct
    void warnIfDisabled() {
        if (!properties.isEnabled()) {
            log.warn("[图形验证码] insure.captcha.enabled=false,登录/注册/发短信均不校验图形验证码,仅限本地联调!");
        }
        if (properties.isDebug()) {
            log.warn("[图形验证码] 当前为 debug 演示模式:验证码明文会随接口回显。"
                    + "生产必须把 insure.captcha.debug 置为 false");
        }
    }

    @Override
    public CaptchaVO generate(CaptchaSceneEnum scene) {
        CaptchaSceneEnum target = scene == null ? CaptchaSceneEnum.LOGIN : scene;
        int length = Math.max(4, Math.min(properties.getLength(), 6));
        String code = randomCode(length);
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        String key = key(target, captchaId);
        redisTemplate.opsForValue().set(key, Sm3Utils.hashHex(code) + "|" + UNVERIFIED + "|",
                Duration.ofSeconds(properties.getExpireSeconds()));
        redisTemplate.delete(failKey(target, captchaId));

        byte[] png = draw(code);
        return new CaptchaVO(captchaId, Base64.getEncoder().encodeToString(png),
                properties.getExpireSeconds(), properties.isDebug() ? code : null);
    }

    @Override
    public void verifyForLogin(String captchaId, String captchaCode) {
        if (!properties.isEnabled()) {
            return;
        }
        CaptchaSceneEnum scene = CaptchaSceneEnum.LOGIN;
        Stored stored = loadAndMatch(scene, captchaId, captchaCode);
        consume(key(scene, stored.captchaId()), failKey(scene, stored.captchaId()));
    }

    @Override
    public void verifyForSms(String captchaId, String captchaCode, String phone) {
        if (!properties.isEnabled()) {
            return;
        }
        CaptchaSceneEnum scene = CaptchaSceneEnum.REGISTER;
        Stored stored = loadAndMatch(scene, captchaId, captchaCode);
        if (stored.verified()) {
            // 一张图只能换一次短信:防止同一验证码给不同手机号反复发码
            throw new BusinessException(ResultCode.CAPTCHA_EXPIRED.getCode(),
                    "图形验证码已使用,请点击图片刷新后重试");
        }
        String mobile = phone == null ? "" : phone.trim();
        String key = key(scene, stored.captchaId());
        Long remain = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        long ttl = remain == null || remain < MIN_REMAIN_SECONDS
                ? Math.max(MIN_REMAIN_SECONDS, properties.getExpireSeconds())
                : remain;
        redisTemplate.opsForValue().set(key,
                stored.digest() + "|" + VERIFIED + "|" + mobile, Duration.ofSeconds(ttl));
        log.debug("[图形验证码] 发短信前校验通过,已绑定手机号: scene={} phone={}",
                scene.getCode(), maskPhone(mobile));
    }

    @Override
    public void verifyForRegister(String captchaId, String captchaCode, String phone, boolean requireSmsVerified) {
        if (!properties.isEnabled()) {
            return;
        }
        CaptchaSceneEnum scene = CaptchaSceneEnum.REGISTER;
        Stored stored = loadAndMatch(scene, captchaId, captchaCode);
        if (requireSmsVerified) {
            if (!stored.verified()) {
                // 没有经过「发短信前校验」,说明调用方绕过了正常注册流程
                throw new BusinessException(ResultCode.CAPTCHA_NOT_VERIFIED);
            }
            String mobile = phone == null ? "" : phone.trim();
            if (!mobile.equals(stored.phone())) {
                throw new BusinessException(ResultCode.CAPTCHA_PHONE_MISMATCH);
            }
        }
        consume(key(scene, stored.captchaId()), failKey(scene, stored.captchaId()));
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 读取验证码、比对答案并记录失败次数
     * <p>答案统一转大写后用 SM3 摘要比对,不区分大小写;连续答错超限立即作废整张验证码。</p>
     */
    private Stored loadAndMatch(CaptchaSceneEnum scene, String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new BusinessException(ResultCode.CAPTCHA_REQUIRED);
        }
        String id = captchaId.trim();
        String key = key(scene, id);
        String raw = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(ResultCode.CAPTCHA_EXPIRED);
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length < FIELD_COUNT) {
            redisTemplate.delete(key);
            throw new BusinessException(ResultCode.CAPTCHA_EXPIRED);
        }

        String failKey = failKey(scene, id);
        Long fails = redisTemplate.opsForValue().increment(failKey);
        if (fails != null && fails == 1L) {
            redisTemplate.expire(failKey, Duration.ofSeconds(properties.getExpireSeconds()));
        }
        if (fails != null && fails > properties.getMaxVerifyAttempts()) {
            consume(key, failKey);
            log.warn("[图形验证码] 连续答错超限,已作废: scene={}", scene.getCode());
            throw new BusinessException(ResultCode.CAPTCHA_EXPIRED);
        }
        String normalized = captchaCode.trim().toUpperCase(Locale.ROOT);
        if (!Sm3Utils.hashHex(normalized).equals(parts[0])) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR);
        }
        redisTemplate.delete(failKey);
        return new Stored(id, parts[0], VERIFIED.equals(parts[1]), parts[2]);
    }

    /**
     * 一次性消费
     */
    private void consume(String key, String failKey) {
        redisTemplate.delete(key);
        redisTemplate.delete(failKey);
    }

    private String key(CaptchaSceneEnum scene, String captchaId) {
        return KEY_PREFIX + scene.getCode() + ":" + captchaId;
    }

    private String failKey(CaptchaSceneEnum scene, String captchaId) {
        return FAIL_PREFIX + scene.getCode() + ":" + captchaId;
    }

    private String randomCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(CHARS[RANDOM.nextInt(CHARS.length)]);
        }
        return builder.toString();
    }

    /**
     * 绘制验证码图片:浅色底 + 干扰线 + 噪点 + 轻微旋转的字符
     * <p>纯 JDK AWT 实现,不引入第三方验证码库;输出 PNG 字节,由控制器转 Base64。</p>
     */
    private byte[] draw(String code) {
        int width = Math.max(100, properties.getWidth());
        int height = Math.max(36, properties.getHeight());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(245, 248, 253));
            graphics.fillRect(0, 0, width, height);

            // 干扰线
            graphics.setStroke(new BasicStroke(1.2f));
            for (int i = 0; i < 6; i++) {
                graphics.setColor(new Color(150 + RANDOM.nextInt(80), 170 + RANDOM.nextInt(70), 200 + RANDOM.nextInt(50)));
                graphics.drawLine(RANDOM.nextInt(width), RANDOM.nextInt(height),
                        RANDOM.nextInt(width), RANDOM.nextInt(height));
            }
            // 噪点
            for (int i = 0; i < 50; i++) {
                graphics.setColor(new Color(190 + RANDOM.nextInt(60), 195 + RANDOM.nextInt(60), 210 + RANDOM.nextInt(45)));
                graphics.fillOval(RANDOM.nextInt(width), RANDOM.nextInt(height), 2, 2);
            }

            Font font = new Font(Font.SANS_SERIF, Font.BOLD, Math.min(28, height - 10));
            graphics.setFont(font);
            int charWidth = (width - 16) / code.length();
            for (int i = 0; i < code.length(); i++) {
                AffineTransform old = graphics.getTransform();
                AffineTransform transform = new AffineTransform(old);
                int x = 8 + i * charWidth;
                int y = height - 10 - RANDOM.nextInt(5);
                transform.rotate((RANDOM.nextDouble() - 0.5) * 0.4, x + charWidth / 2.0, y - 6.0);
                graphics.setTransform(transform);
                graphics.setColor(new Color(30 + RANDOM.nextInt(60), 55 + RANDOM.nextInt(60), 110 + RANDOM.nextInt(70)));
                graphics.drawString(String.valueOf(code.charAt(i)), x, y);
                graphics.setTransform(old);
            }
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException e) {
            log.error("[图形验证码] 图片生成失败", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "验证码图片生成失败,请稍后重试");
        }
    }

    private String maskPhone(String phone) {
        return phone == null || phone.length() < 7 ? "***" : phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /**
     * Redis 中的验证码状态
     *
     * @param captchaId 验证码 ID
     * @param digest    SM3 摘要
     * @param verified  是否已在发短信前校验通过
     * @param phone     绑定的手机号
     */
    private record Stored(String captchaId, String digest, boolean verified, String phone) {
    }
}
