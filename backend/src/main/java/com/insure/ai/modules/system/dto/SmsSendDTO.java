package com.insure.ai.modules.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 短信验证码发送参数
 *
 * @author shaoyong.wang
 */
@Data
@Schema(description = "短信验证码发送参数")
public class SmsSendDTO implements Serializable {

    /** 接收手机号 */
    @Schema(description = "手机号", example = "13900000001")
    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 使用场景:register / login / reset */
    @Schema(description = "使用场景:register 注册、login 登录、reset 重置密码", example = "register")
    private String scene = "register";
}
