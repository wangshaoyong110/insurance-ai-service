package com.insure.ai.modules.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.SmsSceneEnum;
import com.insure.ai.common.util.ClientIpUtils;
import com.insure.ai.config.SmsProperties;
import com.insure.ai.modules.system.dto.SmsCodeVO;
import com.insure.ai.modules.system.entity.CusUser;
import com.insure.ai.modules.system.mapper.CusUserMapper;
import com.insure.ai.modules.system.service.SmsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 短信验证码实现
 * <p>
 * 存储与频控全部走 Redis:验证码按「场景 + 手机号」隔离,重发间隔、手机号/IP/全站
 * 三级发送量上限用独立 key 计数,多实例部署共享状态,不依赖本地内存。
 * </p>
 * <p>
 * <b>防刷设计</b>:发送前按「场景白名单 → 已注册手机号 → 同 IP 小时/日上限 →
 * 全站日总量 → 手机号日上限 → 60 秒重发间隔」依次拦截,任一环节不过都不生成验证码、
 * 不调用发送通道。Redis 异常时 fail-closed 拒绝发送(短信是付费资源,宁可不发也不能无限发)。
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
    /** 手机号每日发送量 key 前缀 */
    private static final String DAILY_KEY = "sms:daily:";
    /** 同 IP 一小时发送量 key 前缀 */
    private static final String IP_HOUR_KEY = "sms:ip:hour:";
    /** 同 IP 每自然日发送量 key 前缀 */
    private static final String IP_DAY_KEY = "sms:ip:day:";
    /** 全站每自然日发送量 key 前缀 */
    private static final String GLOBAL_DAY_KEY = "sms:global:day:";
    /** 校验失败次数 key 后缀 */
    private static final String FAIL_SUFFIX = ":fail";

    /** 自然日后缀,用于「按天」计数的 key */
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 自然日 key 的 TTL:略长于一天,保证当天计数跨零点后自然过期 */
    private static final Duration DAY_TTL = Duration.ofHours(26);
    /** 手机号格式:服务层再校验一次,防止内部调用写入畸形 key */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /** 验证码随机源:使用 SecureRandom,避免可预测的验证码 */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final SmsProperties properties;
    private final CusUserMapper cusUserMapper;

    /**
     * mock 模式下验证码会回显给任意调用方,等于注册校验可被绕过,
     * 因此在启动日志里持续告警,避免误带到生产环境。
     */
    @PostConstruct
    void warnIfMockMode() {
        if (properties.isMock()) {
            log.warn("[短信] 当前为 mock 演示模式:不调用运营商且验证码随接口回显。"
                    + "接入真实通道后必须把 insure.sms.mock 置为 false,否则任意手机号都可被注册");
        }
    }

    @Override
    public SmsCodeVO sendVerifyCode(String phone, SmsSceneEnum scene, String clientIp) {
        String mobile = phone == null ? "" : phone.trim();
        if (!PHONE_PATTERN.matcher(mobile).matches()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "手机号格式不正确");
        }
        SmsSceneEnum target = scene == null ? SmsSceneEnum.REGISTER : scene;

        // 顺序要点:先做只读/低成本的风控判断,最后才占用 60 秒重发额度并生成验证码
        checkSceneAllowed(target);
        checkPhoneNotRegistered(mobile, target);
        checkRiskControl(clientIp);
        checkDailyLimit(mobile);
        checkResendInterval(mobile, target);

        String code = randomCode(properties.getCodeLength());
        String codeKey = codeKey(mobile, target);
        redisTemplate.opsForValue().set(codeKey, code, Duration.ofSeconds(properties.getExpireSeconds()));
        redisTemplate.delete(codeKey + FAIL_SUFFIX);

        doSend(mobile, code, target);

        String tip = properties.isMock()
                ? "演示环境未接入短信运营商,验证码已直接返回"
                : "验证码已发送,请注意查收";
        log.info("[短信] 验证码已发送: phone={} scene={} ip={}",
                maskPhone(mobile), target.getCode(), clientIp);
        return new SmsCodeVO(mobile, target.getCode(), properties.getExpireSeconds(),
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
     * 场景白名单:未开放的场景(如尚未实现短信登录 / 找回密码)一律拒绝,
     * 否则攻击者可用任意场景参数变换 Redis key 绕开重发间隔。
     */
    private void checkSceneAllowed(SmsSceneEnum scene) {
        List<String> supported = properties.getSupportedScenes();
        boolean allowed = supported != null && supported.stream()
                .anyMatch(item -> item != null && item.trim().equalsIgnoreCase(scene.getCode()));
        if (!allowed) {
            log.warn("[短信风控] 未开放场景被拒绝: scene={}", scene.getCode());
            throw new BusinessException(ResultCode.SMS_SCENE_NOT_ALLOWED.getCode(),
                    "该短信场景暂未开放,当前仅支持注册");
        }
    }

    /**
     * 注册场景不给已注册手机号发码:既省短信费,也避免被用来反复骚扰存量用户
     */
    private void checkPhoneNotRegistered(String phone, SmsSceneEnum scene) {
        if (scene != SmsSceneEnum.REGISTER) {
            return;
        }
        Long count = cusUserMapper.selectCount(Wrappers.<CusUser>lambdaQuery()
                .eq(CusUser::getPhone, phone));
        if (count != null && count > 0) {
            log.warn("[短信风控] 已注册手机号仍请求注册验证码: phone={}", maskPhone(phone));
            throw new BusinessException(ResultCode.SMS_PHONE_REGISTERED);
        }
    }

    /**
     * 多维发送量风控:同 IP 小时 / 同 IP 自然日 / 全站自然日
     * <p>手机号维度的上限只能挡住「盯着一个号打」,换号轰炸和代理池刷量必须靠 IP 与全局维度。</p>
     */
    private void checkRiskControl(String clientIp) {
        String ip = StringUtils.hasText(clientIp) ? clientIp : ClientIpUtils.UNKNOWN;
        String day = LocalDate.now().format(DAY_FORMAT);
        bumpAndCheck(IP_HOUR_KEY + ip, properties.getIpHourlyLimit(), Duration.ofHours(1),
                ResultCode.SMS_RISK_REJECTED, "同一网络一小时内获取验证码次数过多,请稍后再试");
        bumpAndCheck(IP_DAY_KEY + ip + ":" + day, properties.getIpDailyLimit(), DAY_TTL,
                ResultCode.SMS_RISK_REJECTED, "同一网络今日获取验证码次数过多,请明天再试");
        bumpAndCheck(GLOBAL_DAY_KEY + day, properties.getGlobalDailyLimit(), DAY_TTL,
                ResultCode.SMS_RISK_REJECTED, "今日验证码发送量已达上限,请明天再试");
        log.debug("[短信风控] ip={} 通过发送量检查", ip);
    }

    /**
     * 手机号每日发送上限(按自然日计)
     */
    private void checkDailyLimit(String phone) {
        String day = LocalDate.now().format(DAY_FORMAT);
        bumpAndCheck(DAILY_KEY + phone + ":" + day, properties.getDailyLimit(), DAY_TTL,
                ResultCode.SMS_DAILY_LIMIT,
                "该手机号今日验证码获取次数已达上限(" + properties.getDailyLimit() + " 次),请明天再试");
    }

    /**
     * 计数并判限:首次写入时设置 TTL,后续请求不刷新窗口;
     * Redis 异常或返回空值时 fail-closed,拒绝发送。
     */
    private void bumpAndCheck(String key, int limit, Duration ttl, ResultCode resultCode, String message) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, ttl);
            }
            if (count == null || count > limit) {
                log.warn("[短信风控] 命中发送上限: key={} count={} limit={}", key, count, limit);
                throw new BusinessException(resultCode.getCode(), message);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[短信风控] Redis 不可用,按 fail-closed 拒绝发送: key={}", key, e);
            throw new BusinessException(ResultCode.SMS_RISK_REJECTED.getCode(), "服务繁忙,请稍后再试");
        }
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
