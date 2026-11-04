package com.insure.ai.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录请求参数
 *
 * @author insurance-ai
 */
@Data
public class LoginDTO implements Serializable {

    /** 登录账号或手机号 */
    @NotBlank(message = "请输入账号或手机号")
    private String account;

    /** 登录密码 */
    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 32, message = "密码长度为 6-32 位")
    private String password;
}
