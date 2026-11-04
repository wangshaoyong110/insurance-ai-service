package com.insure.ai.modules.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 后台新增/编辑管理端用户参数
 *
 * @author insurance-ai
 */
@Data
public class UserSaveDTO implements Serializable {

    /** 用户ID,新增时为空 */
    private Long id;

    /** 登录账号 */
    @NotBlank(message = "请输入账号")
    @Size(min = 3, max = 20, message = "账号长度为 3-20 位")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{2,19}$", message = "账号需以字母开头,可包含字母、数字、下划线")
    private String username;

    /** 登录密码,新增必填,编辑时为空表示不修改 */
    @Size(min = 6, max = 32, message = "密码长度为 6-32 位")
    private String password;

    /** 真实姓名 */
    @Size(max = 50, message = "姓名长度不能超过 50 个字符")
    private String realName;

    /** 手机号 */
    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 电子邮箱 */
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 头像地址 */
    private String avatar;

    /** 角色ID */
    @NotNull(message = "请选择角色")
    private Long roleId;

    /** 状态:1-启用 0-禁用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
