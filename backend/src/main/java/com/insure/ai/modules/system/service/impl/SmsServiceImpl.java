package com.insure.ai.modules.system.service.impl;

import com.insure.ai.common.BusinessException;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.SmsSceneEnum;
import com.insure.ai.config.SmsProperties;
import com.insure.ai.modules.system.dto.SmsCodeVO;
import com.insure.ai.modules.system.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 短信验证码实现
 * <p>
 * 存储与频控全部走 Redis:验证码按「场景 + 手机号」隔离,重发间隔与每日上限
 * 用独立 key 计数,多实例部署时天然共享状态,不依赖本地内存。
 * </p>
 *
 * @author shaoyong.wang
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsServiceImpl implements SmsService {

    /** 验证码 key 前缀:sms:code:{scene}:{phone} */
    private static final String CODE_KEY = "sms:code:";
    /** 重发间隔 key 前缀 */
    private static final String RESEND_KEY = "sms:resend:";
    /** 每日发送量 key 前缀 */
    private static final String DAILY_KEY = "sms:daily:";
    /** 校验失败次数 key 后缀 */
    private static final String FAIL_SUFFIX = ":fail";

    /** 验证码随机源:使用 SecureRandom,避免可预测的验证码 */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final SmsProperties properties;

    @Override
    public SmsCodeVO sendVerifyCode(String phone, SmsSceneEnum scene) {
        String mobile = phone == null ? "" : phone.trim();
        if (!StringUtils.hasText(mobile)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请输入手机号");
        }
        checkResendInterval(mobile, scene);
        checkDailyLimit(mobile);

        String code = randomCode(properties.getCodeLength());
        String codeKey = codeKey(mobile, scene);
        redisTemplate.opsForValue().set(codeKey, code, Duration.ofSeconds(properties.getExpireSeconds()));
        redisTemplate.delete(codeKey + FAIL_SUFFIX);

        doSend(mobile, code, scene);

        String tip = properties.isMock()
                ? "演示环境未接入短信运营商,验证码已直接返回"
                : "验证码已发送,请注意查收";
        return new SmsCodeVO(mobile, scene.getCode(), properties.getExpireSeconds(),
                properties.getResendInterval(), properties.isMock() ? code : null, tip);
    }

    @Override
    public void verifyCode(String phone, String code, SmsSceneEnum scene) {
        if (!isRegisterVerifyRequired() && scene == SmsSceneEnum.REGISTER) {
            return;
        }
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ResultCode.SMS_CODE_EMPTY);
        }
        String mobile = phone.trim();
        String codeKey = codeKey(mobile, scene);
        String cached = redisTemplate.opsForValue().get(codeKey);
        if (!StringUtils.hasText(cached)) {
            throw new BusinessException(ResultCode.SMS_CODE_EXPIRED);
        }

        // 先记失败次数再比对:防止穷举 6 位验证码
        String failKey = codeKey + FAIL_SUFFIX;
        Long fails = redisTemplate.opsForValue().increment(failKey);
        if (fails != null && fails == 1L) {
            redisTemplate.expire(failKey, Duration.ofSeconds(properties.getExpireSeconds()));
        }
        if (fails != null && fails > properties.getMaxVerifyAttempts()) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(failKey);
            log.warn("[短信] 验证码校验失败次数超限,已作废: scene={} phone={}", scene.getCode(), maskPhone(mobile));
            throw new BusinessException(ResultCode.SMS_CODE_EXPIRED);
        }
        if (!cached.equals(code.trim())) {
            throw new BusinessException(ResultCode.SMS_CODE_ERROR);
        }
        // 验证码一次性使用
        redisTemplate.delete(codeKey);
        redisTemplate.delete(failKey);
        log.info("[短信] 验证码校验通过: scene={} phone={}", scene.getCode(), maskPhone(mobile));
    }

    @Override
    public boolean isRegisterVerifyRequired() {
        return properties.isVerifyRegister();
    }

    /**
     * 重发间隔校验
     */
    private void checkResendInterval(String phone, SmsSceneEnum scene) {
        String key = RESEND_KEY + scene.getCode() + ":" + phone;
        Boolean absent = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(properties.getResendInterval()));
        if (Boolean.FALSE.equals(absent)) {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            long wait = ttl == null || ttl < 0 ? properties.getResendInterval() : ttl;
            throw new BusinessException(ResultCode.SMS_SEND_TOO_FREQUENT.getCode(),
                    "请 " + wait + " 秒后再获取验证码");
        }
    }

    /**
     * 每日发送上限校验
     */
    private void checkDailyLimit(String phone) {
        String key = DAILY_KEY + phone;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofDays(1));
        }
        if (count != null && count > properties.getDailyLimit()) {
            throw new BusinessException(ResultCode.SMS_DAILY_LIMIT.getCode(),
                    "今日验证码获取次数已达上限(" + properties.getDailyLimit() + " 次),请明天再试");
        }
    }

    /**
     * 实际发送
     * <p>
     * <b>TODO 对接短信运营商</b>:当前未接入任何通道,只打印日志并视为发送成功,
     * 便于在没有短信资质的环境下把注册流程跑通。接入时按下方示例替换实现,
     * 并把 {@code insure.sms.mock} 置为 false。
     * </p>
     * <pre>{@code
     * // 阿里云示例(需引入 com.aliyun:dysmsapi20170525)
     * SendSmsRequest request = new SendSmsRequest()
     *         .setPhoneNumbers(phone)
     *         .setSignName(properties.getSignName())
     *         .setTemplateCode(properties.getTemplateCode())
     *         .setTemplateParam("{\"code\":\"" + code + "\"}");
     * client.sendSms(request);
     *
     * // 腾讯云示例(需引入 com.tencentcloudapi:tencentcloud-sdk-java-sms)
     * SendSmsRequest req = new SendSmsRequest();
     * req.setPhoneNumberSet(new String[]{"+86" + phone});
     * req.setSmsSdkAppId(appId);
     * req.setSignName(properties.getSignName());
     * req.setTemplateId(properties.getTemplateCode());
     * req.setTemplateParamSet(new String[]{code, String.valueOf(properties.getExpireSeconds() / 60)});
     * smsClient.SendSms(req);
     * }</pre>
     *
     * @param phone 手机号
     * @param code  验证码
     * @param scene 使用场景
     */
    private void doSend(String phone, String code, SmsSceneEnum scene) {
        // 未接入运营商:直接返回成功(TODO 见方法注释)
        log.info("[短信-模拟发送] 手机号={} 场景={} 验证码={} 有效期={}秒 签名={} 模板={}",
                maskPhone(phone), scene.getLabel(), code, properties.getExpireSeconds(),
                properties.getSignName(), properties.getTemplateCode());
    }

    /**
     * 生成指定位数的数字验证码
     */
    private String randomCode(int length) {
        int len = length < 4 ? 6 : length;
        StringBuilder builder = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }

    /**
     * 验证码存储 key
     */
    private String codeKey(String phone, SmsSceneEnum scene) {
        return CODE_KEY + scene.getCode() + ":" + phone;
    }

    /**
     * 日志手机号脱敏
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
