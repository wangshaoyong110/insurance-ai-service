package com.insure.ai.modules.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 短信验证码发送结果
 *
 * @param phone         手机号
 * @param scene         使用场景
 * @param expireSeconds 有效期(秒)
 * @param resendSeconds 下次可重发时间(秒)
 * @param debugCode     演示环境回显的验证码,接入真实通道后恒为 null
 * @param tip           给前端的提示语
 * @author shaoyong.wang
 */
@Schema(description = "短信验证码发送结果")
public record SmsCodeVO(String phone,
                        String scene,
                        int expireSeconds,
                        int resendSeconds,
                        String debugCode,
                        String tip) {
}
