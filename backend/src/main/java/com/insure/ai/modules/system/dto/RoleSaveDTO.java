package com.insure.ai.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 角色新增/编辑参数
 *
 * @author insurance-ai
 */
@Data
public class RoleSaveDTO implements Serializable {

    /** 角色ID,新增时为空 */
    private Long id;

    /** 角色标识 */
    @NotBlank(message = "请输入角色标识")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,29}$", message = "角色标识需为大写字母、数字或下划线,且以字母开头")
    private String roleCode;

    /** 角色名称 */
    @NotBlank(message = "请输入角色名称")
    @Size(max = 50, message = "角色名称不能超过 50 个字符")
    private String roleName;

    /** 排序 */
    private Integer sort;

    /** 状态:1-启用 0-禁用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 授权菜单ID集合 */
    private List<Long> menuIds;
}
