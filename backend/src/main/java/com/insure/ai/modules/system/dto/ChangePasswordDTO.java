package com.insure.ai.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 修改密码参数
 *
 * @author insurance-ai
 */
@Data
public class ChangePasswordDTO implements Serializable {

    /** 原密码 */
    @NotBlank(message = "请输入原密码")
    private String oldPassword;

    /** 新密码 */
    @NotBlank(message = "请输入新密码")
    @Size(min = 6, max = 32, message = "密码长度为 6-32 位")
    private String newPassword;
}
