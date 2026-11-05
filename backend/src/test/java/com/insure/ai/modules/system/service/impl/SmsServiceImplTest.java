package com.insure.ai.modules.system.service.impl;

import com.insure.ai.common.BusinessException;
import com.insure.ai.common.enums.SmsSceneEnum;
import com.insure.ai.config.SmsProperties;
import com.insure.ai.modules.system.dto.SmsCodeVO;
import com.insure.ai.modules.system.mapper.CusUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 短信发送风控单测
 * <p>验证码是要花钱的资源,任何「本该拦截却放行」的回归都属于线上事故,逐条锁死</p>
 *
 * @author shaoyong.wang
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("短信验证码防刷")
class SmsServiceImplTest {

    private static final String PHONE = "13900008888";
    private static final String IP = "203.0.113.9";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private CusUserMapper cusUserMapper;

    private SmsProperties properties;
    private SmsServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new SmsProperties();
        properties.setMock(false);
        properties.setSupportedScenes(new ArrayList<>(List.of("register")));
        properties.setIpHourlyLimit(20);
        properties.setIpDailyLimit(50);
        properties.setGlobalDailyLimit(1000);
        properties.setDailyLimit(10);
        properties.setResendInterval(60);
        properties.setExpireSeconds(300);

        service = new SmsServiceImpl(redisTemplate, properties, cusUserMapper);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // 默认:所有计数都是第一次命中,未超任何上限
        lenient().when(valueOperations.increment(anyString())).thenReturn(1L);
        lenient().when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        lenient().when(cusUserMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    @DisplayName("未开放场景(短信登录)直接拒绝,不能靠换 scene 绕过频控")
    void unsupportedSceneShouldBeRejected() {
        expectCode(() -> service.sendVerifyCode(PHONE, SmsSceneEnum.LOGIN, IP), 4008);
    }

    @Test
    @DisplayName("已注册手机号不再发送注册验证码,避免骚扰存量用户")
    void registeredPhoneShouldBeRejected() {
        when(cusUserMapper.selectCount(any())).thenReturn(1L);

        expectCode(() -> service.sendVerifyCode(PHONE, SmsSceneEnum.REGISTER, IP), 4010);
    }

    @Test
    @DisplayName("同一 IP 一小时内换不同手机号刷量会被拦截")
    void ipHourlyLimitShouldBeRejected() {
        when(valueOperations.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            return key.startsWith("sms:ip:hour:") ? 21L : 1L;
        });

        expectCode(() -> service.sendVerifyCode(PHONE, SmsSceneEnum.REGISTER, IP), 4009);
    }

    @Test
    @DisplayName("同一 IP 自然日上限被突破时拒绝发送")
    void ipDailyLimitShouldBeRejected() {
        when(valueOperations.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            return key.startsWith("sms:ip:day:") ? 51L : 1L;
        });

        expectCode(() -> service.sendVerifyCode(PHONE, SmsSceneEnum.REGISTER, IP), 4009);
    }

    @Test
    @DisplayName("全站日总量上限兜底,防止代理池分布式刷量打爆短信预算")
    void globalDailyLimitShouldBeRejected() {
        when(valueOperations.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            return key.startsWith("sms:global:day:") ? 1001L : 1L;
        });

        expectCode(() -> service.sendVerifyCode(PHONE, SmsSceneEnum.REGISTER, IP), 4009);
    }

    @Test
    @DisplayName("手机号日上限单独生效")
    void phoneDailyLimitShouldBeRejected() {
        when(valueOperations.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            return key.startsWith("sms:daily:") ? 11L : 1L;
        });

        expectCode(() -> service.sendVerifyCode(PHONE, SmsSceneEnum.REGISTER, IP), 4004);
    }

    @Test
    @DisplayName("Redis 异常时 fail-closed 拒绝发送,而不是无限放行")
    void redisFailureShouldRejectSending() {
        when(valueOperations.increment(anyString())).thenThrow(new IllegalStateException("redis down"));

        expectCode(() -> service.sendVerifyCode(PHONE, SmsSceneEnum.REGISTER, IP), 4009);
    }

    @Test
    @DisplayName("60 秒重发间隔仍生效")
    void resendIntervalShouldStillApply() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        expectCode(() -> service.sendVerifyCode(PHONE, SmsSceneEnum.REGISTER, IP), 4003);
    }

    @Test
    @DisplayName("风控全部通过后正常写验证码;非 mock 模式绝不回显明文")
    void normalSendShouldWriteCodeWithoutEcho() {
        SmsCodeVO result = service.sendVerifyCode(PHONE, SmsSceneEnum.REGISTER, IP);

        assertThat(result.debugCode()).isNull();
        assertThat(result.phone()).isEqualTo(PHONE);
        verify(valueOperations).set(startsWith("sms:code:register:"), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("手机号格式在服务层再次校验,防止内部调用写入畸形 key")
    void invalidPhoneShouldBeRejected() {
        expectCode(() -> service.sendVerifyCode("not-a-phone", SmsSceneEnum.REGISTER, IP), 400);
    }

    private void expectCode(Runnable action, int code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(code));
    }
}
