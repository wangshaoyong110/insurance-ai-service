package com.insure.ai.modules.system.service;

import com.insure.ai.common.enums.CaptchaSceneEnum;
import com.insure.ai.modules.system.dto.CaptchaVO;

/**
 * 图形验证码服务
 * <p>
 * 与短信验证码职责分离:图形验证码负责「证明当前是人机交互」,短信验证码负责
 * 「证明手机号的持有者」。注册链路要求先通过图形验证码才允许发短信,
 * 避免短信通道被脚本直接调用。
 * </p>
 *
 * @author shaoyong.wang
 */
public interface CaptchaService {

    /**
     * 生成一张图形验证码
     *
     * @param scene 使用场景(登录 / 注册),不同场景的验证码互不通用
     * @return 验证码 ID 与 PNG 图片
     */
    CaptchaVO generate(CaptchaSceneEnum scene);

    /**
     * 登录校验:一次性消费,先于账号密码校验执行,避免用错误验证码把他人账号锁定
     *
     * @param captchaId   验证码 ID
     * @param captchaCode 用户输入的字符
     */
    void verifyForLogin(String captchaId, String captchaCode);

    /**
     * 注册场景:发送短信前校验
     * <p>校验通过后不消费,而是把验证码标记为「已验证」并绑定手机号;
     * 注册提交时复核同一张验证码,保证短信确实是在图形校验通过后发出的。</p>
     *
     * @param captchaId   验证码 ID
     * @param captchaCode 用户输入的字符
     * @param phone       本次发短信的手机号
     */
    void verifyForSms(String captchaId, String captchaCode, String phone);

    /**
     * 注册场景:注册提交时复核并消费
     *
     * @param captchaId          验证码 ID
     * @param captchaCode        用户输入的字符
     * @param phone              注册手机号,必须与发短信时绑定的一致
     * @param requireSmsVerified 是否要求已经过发短信前的图形校验
     *                           (本地联调关闭短信校验时传 false)
     */
    void verifyForRegister(String captchaId, String captchaCode, String phone, boolean requireSmsVerified);

    /**
     * 图形验证码总开关是否开启
     *
     * @return true 表示所有业务请求必须携带并校验图形验证码
     */
    boolean isEnabled();
}
