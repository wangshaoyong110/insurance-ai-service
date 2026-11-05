package com.insure.ai.modules.system.service;

import com.insure.ai.common.enums.SmsSceneEnum;
import com.insure.ai.modules.system.dto.SmsCodeVO;

/**
 * 短信验证码服务
 * <p>
 * 负责验证码的生成、频控、发送与校验。运营商通道尚未接入,
 * 发送环节目前只做模拟(见实现类 TODO),但校验链路是完整的:
 * 验证码写 Redis、按场景隔离、限制重发间隔与每日上限、限制校验失败次数。
 * </p>
 *
 * @author shaoyong.wang
 */
public interface SmsService {

    /**
     * 发送验证码
     *
     * @param phone 手机号
     * @param scene 使用场景
     * @return 发送结果(演示环境含回显验证码)
     */
    SmsCodeVO sendVerifyCode(String phone, SmsSceneEnum scene);

    /**
     * 校验验证码,失败抛业务异常
     *
     * @param phone 手机号
     * @param code  用户输入的验证码
     * @param scene 使用场景
     */
    void verifyCode(String phone, String code, SmsSceneEnum scene);

    /**
     * 注册场景是否需要强制校验验证码
     *
     * @return true 表示注册必须带验证码
     */
    boolean isRegisterVerifyRequired();
}
