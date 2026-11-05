package com.insure.ai.modules.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 图形验证码
 *
 * @param captchaId     验证码 ID,提交业务请求时原样带回
 * @param image         PNG 图片的 Base64 字符串(不含 data: 前缀),前端拼 data:image/png;base64, 显示
 * @param expireSeconds 有效期(秒)
 * @param debugCode     演示模式回显的验证码明文;生产关闭 debug 后恒为 null
 * @author shaoyong.wang
 */
@Schema(description = "图形验证码")
public record CaptchaVO(String captchaId,
                        String image,
                        int expireSeconds,
                        String debugCode) {
}
