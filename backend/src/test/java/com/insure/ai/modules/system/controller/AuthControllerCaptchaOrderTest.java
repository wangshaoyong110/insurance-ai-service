package com.insure.ai.modules.system.controller;

import com.insure.ai.common.BusinessException;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.CaptchaSceneEnum;
import com.insure.ai.common.enums.SmsSceneEnum;
import com.insure.ai.config.SecurityProperties;
import com.insure.ai.modules.system.dto.LoginDTO;
import com.insure.ai.modules.system.dto.SmsCodeVO;
import com.insure.ai.modules.system.dto.SmsSendDTO;
import com.insure.ai.modules.system.service.CaptchaService;
import com.insure.ai.modules.system.service.CusAuthService;
import com.insure.ai.modules.system.service.SmsService;
import com.insure.ai.security.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证控制器图形验证码顺序单测
 * <p>需求硬约束:<b>注册必须先把图形验证码验证无误,才允许发短信</b>。
 * 这里锁死调用顺序与失败短路,避免后续重构把校验挪到风控之后。</p>
 *
 * @author shaoyong.wang
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("认证接口图形验证码前置校验")
class AuthControllerCaptchaOrderTest {

    private static final String PHONE = "13900008888";
    private static final String CAPTCHA_ID = "captcha-001";
    private static final String CAPTCHA_CODE = "7K3M";

    @InjectMocks
    private AuthController controller;

    @Mock
    private CusAuthService cusAuthService;
    @Mock
    private SmsService smsService;
    @Mock
    private CaptchaService captchaService;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private SecurityProperties securityProperties;
    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        lenient().when(securityProperties.getTrustedProxies()).thenReturn(List.of());
    }

    @Test
    @DisplayName("发短信前先校验图形验证码,通过后才调用短信服务")
    void captchaShouldBeVerifiedBeforeSms() {
        SmsSendDTO dto = smsDTO();
        when(smsService.sendVerifyCode(anyString(), any(SmsSceneEnum.class), anyString()))
                .thenReturn(new SmsCodeVO(PHONE, "register", 300, 60, null, "验证码已发送"));

        controller.sendSmsCode(dto, request);

        InOrder order = inOrder(captchaService, smsService);
        order.verify(captchaService).verifyForSms(CAPTCHA_ID, CAPTCHA_CODE, PHONE);
        order.verify(smsService).sendVerifyCode(eq(PHONE), eq(SmsSceneEnum.REGISTER), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("图形验证码错误时完全不发短信、不进短信风控")
    void wrongCaptchaShouldBlockSms() {
        SmsSendDTO dto = smsDTO();
        org.mockito.Mockito.doThrow(new BusinessException(ResultCode.CAPTCHA_ERROR))
                .when(captchaService).verifyForSms(CAPTCHA_ID, CAPTCHA_CODE, PHONE);

        assertThatThrownBy(() -> controller.sendSmsCode(dto, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.CAPTCHA_ERROR.getCode());
        verify(smsService, never()).sendVerifyCode(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("登录接口把图形验证码参数透传给认证服务")
    void loginShouldPassCaptchaParams() {
        LoginDTO dto = new LoginDTO();
        dto.setAccount(PHONE);
        dto.setPassword("123456");
        dto.setCaptchaId(CAPTCHA_ID);
        dto.setCaptchaCode(CAPTCHA_CODE);

        controller.login(dto, request);

        org.mockito.ArgumentCaptor<LoginDTO> captor = org.mockito.ArgumentCaptor.forClass(LoginDTO.class);
        verify(cusAuthService).login(captor.capture(), eq(request));
        assertThat(captor.getValue().getCaptchaId()).isEqualTo(CAPTCHA_ID);
        assertThat(captor.getValue().getCaptchaCode()).isEqualTo(CAPTCHA_CODE);
    }

    @Test
    @DisplayName("获取图形验证码时非法场景返回 4014,且不生成验证码")
    void invalidSceneShouldBeRejected() {
        assertThatThrownBy(() -> controller.captcha("unknown"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.CAPTCHA_SCENE_ERROR.getCode());
        verify(captchaService, never()).generate(any(CaptchaSceneEnum.class));
    }

    @Test
    @DisplayName("注册场景按 register 生成验证码")
    void registerSceneShouldGenerateRegisterCaptcha() {
        controller.captcha("register");
        verify(captchaService).generate(CaptchaSceneEnum.REGISTER);
    }

    private SmsSendDTO smsDTO() {
        SmsSendDTO dto = new SmsSendDTO();
        dto.setPhone(PHONE);
        dto.setScene("register");
        dto.setCaptchaId(CAPTCHA_ID);
        dto.setCaptchaCode(CAPTCHA_CODE);
        return dto;
    }
}
