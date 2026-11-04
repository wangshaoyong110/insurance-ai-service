package com.insure.ai.modules.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 客户端注册请求参数
 *
 * @author insurance-ai
 */
@Data
public class RegisterDTO implements Serializable {

    /** 登录账号(可选,为空时默认使用手机号) */
    @Pattern(regexp = "^$|^[a-zA-Z][a-zA-Z0-9_]{2,19}$", message = "账号需以字母开头,可包含字母、数字、下划线")
    private String username;

    /** 登录密码 */
    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 32, message = "密码长度为 6-32 位")
    private String password;

    /** 确认密码 */
    @NotBlank(message = "请再次输入密码")
    private String confirmPassword;

    /** 昵称 */
    @Size(max = 50, message = "昵称长度不能超过 50 个字符")
    private String nickname;

    /** 真实姓名 */
    @Size(max = 50, message = "姓名长度不能超过 50 个字符")
    private String realName;

    /** 手机号(全局唯一) */
    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 电子邮箱 */
    @Email(message = "邮箱格式不正确")
    private String email;
}
