package com.insure.ai.modules.system.service.impl;

import com.insure.ai.common.BusinessException;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.CaptchaSceneEnum;
import com.insure.ai.config.CaptchaProperties;
import com.insure.ai.modules.system.dto.CaptchaVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 图形验证码单测
 * <p>用内存 Map 模拟 Redis,覆盖答案摘要、一次性消费、场景隔离与注册链路的
 * 「发短信前校验 → 注册提交复核」状态流转,防止后续重构把顺序改坏。</p>
 *
 * @author shaoyong.wang
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("图形验证码")
class CaptchaServiceImplTest {

    private static final String PHONE = "13900008888";
    private static final String OTHER_PHONE = "13900007777";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    /** 内存版 Redis */
    private final Map<String, String> store = new HashMap<>();

    private CaptchaProperties properties;
    private CaptchaServiceImpl service;

    @BeforeEach
    void setUp() {
        store.clear();
        properties = new CaptchaProperties();
        properties.setEnabled(true);
        properties.setDebug(true);
        properties.setExpireSeconds(300);
        properties.setLength(4);
        properties.setMaxVerifyAttempts(3);
        service = new CaptchaServiceImpl(redisTemplate, properties);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().doAnswer(invocation -> {
            store.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        lenient().when(valueOperations.get(anyString()))
                .thenAnswer(invocation -> store.get(invocation.<String>getArgument(0)));
        lenient().when(valueOperations.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long next = Long.parseLong(store.getOrDefault(key, "0")) + 1;
            store.put(key, String.valueOf(next));
            return next;
        });
        lenient().when(redisTemplate.delete(anyString())).thenAnswer(invocation ->
                store.remove(invocation.<String>getArgument(0)) != null);
        lenient().when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
        lenient().when(redisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenReturn(200L);
    }

    @Test
    @DisplayName("生成的图片是合法 PNG,Redis 里只存 SM3 摘要不存明文")
    void generateShouldReturnPngAndStoreDigestOnly() {
        CaptchaVO vo = service.generate(CaptchaSceneEnum.LOGIN);

        assertThat(vo.captchaId()).isNotBlank();
        assertThat(vo.debugCode()).hasSize(4);
        byte[] png = Base64.getDecoder().decode(vo.image());
        assertThat(png[0]).isEqualTo((byte) 0x89);
        assertThat(new String(png, 1, 3, StandardCharsets.ISO_8859_1)).isEqualTo("PNG");
        assertThat(store).hasSize(1);
        assertThat(store.values().iterator().next())
                .endsWith("|0|")
                .doesNotContain(vo.debugCode());
    }

    @Test
    @DisplayName("关闭 debug 后不再回显验证码明文")
    void debugOffShouldNotExposeCode() {
        properties.setDebug(false);
        assertThat(service.generate(CaptchaSceneEnum.LOGIN).debugCode()).isNull();
    }

    @Test
    @DisplayName("登录校验成功后立即消费,同一张不能重放")
    void loginShouldConsumeCaptcha() {
        CaptchaVO vo = service.generate(CaptchaSceneEnum.LOGIN);

        assertThatCode(() -> service.verifyForLogin(vo.captchaId(), vo.debugCode())).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.verifyForLogin(vo.captchaId(), vo.debugCode()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.CAPTCHA_EXPIRED.getCode());
    }

    @Test
    @DisplayName("答案不区分大小写")
    void lowercaseShouldBeAccepted() {
        CaptchaVO vo = service.generate(CaptchaSceneEnum.LOGIN);
        assertThatCode(() -> service.verifyForLogin(vo.captchaId(),
                vo.debugCode().toLowerCase(Locale.ROOT))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("验证码错误返回 4012,且不消费原验证码")
    void wrongCodeShouldBeRejected() {
        CaptchaVO vo = service.generate(CaptchaSceneEnum.LOGIN);
        String wrong = vo.debugCode().equals("AAAA") ? "BBBB" : "AAAA";

        assertCode(() -> service.verifyForLogin(vo.captchaId(), wrong), ResultCode.CAPTCHA_ERROR.getCode());
        assertThat(store).containsKey("captcha:login:" + vo.captchaId());
    }

    @Test
    @DisplayName("连续答错超过上限即作废整张验证码")
    void tooManyFailuresShouldInvalidate() {
        CaptchaVO vo = service.generate(CaptchaSceneEnum.LOGIN);
        String wrong = vo.debugCode().equals("AAAA") ? "BBBB" : "AAAA";

        for (int i = 0; i < properties.getMaxVerifyAttempts(); i++) {
            assertCode(() -> service.verifyForLogin(vo.captchaId(), wrong), ResultCode.CAPTCHA_ERROR.getCode());
        }
        assertCode(() -> service.verifyForLogin(vo.captchaId(), vo.debugCode()),
                ResultCode.CAPTCHA_EXPIRED.getCode());
        assertThat(store).doesNotContainKey("captcha:login:" + vo.captchaId());
    }

    @Test
    @DisplayName("缺少验证码参数返回 4011")
    void missingCaptchaShouldBeRejected() {
        assertCode(() -> service.verifyForLogin("", ""), ResultCode.CAPTCHA_REQUIRED.getCode());
    }

    @Test
    @DisplayName("登录场景的验证码不能用于注册场景")
    void scenesShouldBeIsolated() {
        CaptchaVO vo = service.generate(CaptchaSceneEnum.LOGIN);
        assertCode(() -> service.verifyForSms(vo.captchaId(), vo.debugCode(), PHONE),
                ResultCode.CAPTCHA_EXPIRED.getCode());
    }

    @Test
    @DisplayName("图形验证码错误时不会把状态标记为已验证")
    void wrongCodeShouldNotMarkVerified() {
        CaptchaVO vo = service.generate(CaptchaSceneEnum.REGISTER);
        String wrong = vo.debugCode().equals("AAAA") ? "BBBB" : "AAAA";

        assertCode(() -> service.verifyForSms(vo.captchaId(), wrong, PHONE), ResultCode.CAPTCHA_ERROR.getCode());
        assertThat(store.get("captcha:register:" + vo.captchaId())).endsWith("|0|");
    }

    @Test
    @DisplayName("发短信前校验通过:标记为已验证并绑定手机号,但不消费")
    void smsVerificationShouldMarkVerifiedAndBindPhone() {
        CaptchaVO vo = service.generate(CaptchaSceneEnum.REGISTER);

        service.verifyForSms(vo.captchaId(), vo.debugCode(), PHONE);

        assertThat(store.get("captcha:register:" + vo.captchaId()))
                .endsWith("|1|" + PHONE)
                .doesNotContain(vo.debugCode());
    }

    @Test
    @DisplayName("同一张验证码不能换手机号再发一次短信")
    void verifiedCaptchaShouldNotBeReusedForAnotherPhone() {
        CaptchaVO vo = service.generate(CaptchaSceneEnum.REGISTER);
        service.verifyForSms(vo.captchaId(), vo.debugCode(), PHONE);

        assertCode(() -> service.verifyForSms(vo.captchaId(), vo.debugCode(), OTHER_PHONE),
                ResultCode.CAPTCHA_EXPIRED.getCode());
    }

    @Test
    @DisplayName("未经过发短信前校验,注册提交直接拒绝(4015)")
    void registerShouldRequireSmsVerificationFirst() {
        CaptchaVO vo = service.generate(CaptchaSceneEnum.REGISTER);

        assertCode(() -> service.verifyForRegister(vo.captchaId(), vo.debugCode(), PHONE, true),
                ResultCode.CAPTCHA_NOT_VERIFIED.getCode());
        assertThat(store).containsKey("captcha:register:" + vo.captchaId());
    }

    @Test
    @DisplayName("注册手机号与发短信时绑定的不一致时拒绝(4016)")
    void registerShouldRejectPhoneMismatch() {
        CaptchaVO vo = service.generate(CaptchaSceneEnum.REGISTER);
        service.verifyForSms(vo.captchaId(), vo.debugCode(), PHONE);

        assertCode(() -> service.verifyForRegister(vo.captchaId(), vo.debugCode(), OTHER_PHONE, true),
                ResultCode.CAPTCHA_PHONE_MISMATCH.getCode());
    }

    @Test
    @DisplayName("完整注册链路:发短信前校验 → 注册复核 → 一次性消费")
    void registerFlowShouldConsumeCaptcha() {
        CaptchaVO vo = service.generate(CaptchaSceneEnum.REGISTER);
        service.verifyForSms(vo.captchaId(), vo.debugCode(), PHONE);

        assertThatCode(() -> service.verifyForRegister(vo.captchaId(), vo.debugCode(), PHONE, true))
                .doesNotThrowAnyException();
        assertThat(store).doesNotContainKey("captcha:register:" + vo.captchaId());
    }

    @Test
    @DisplayName("关闭短信校验的联调模式下,注册不要求已验证状态")
    void registerWithoutSmsVerifyShouldSkipVerifiedCheck() {
        CaptchaVO vo = service.generate(CaptchaSceneEnum.REGISTER);
        assertThatCode(() -> service.verifyForRegister(vo.captchaId(), vo.debugCode(), PHONE, false))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("总开关关闭时跳过全部校验,便于本地联调")
    void disabledShouldSkipVerification() {
        properties.setEnabled(false);
        assertThat(service.isEnabled()).isFalse();
        assertThatCode(() -> service.verifyForLogin(null, null)).doesNotThrowAnyException();
        assertThatCode(() -> service.verifyForRegister(null, null, PHONE, true)).doesNotThrowAnyException();
    }

    private void assertCode(Runnable action, int expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected);
    }
}
