package com.insure.ai.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 菜单新增/编辑参数
 *
 * @author insurance-ai
 */
@Data
public class MenuSaveDTO implements Serializable {

    /** 菜单ID,新增时为空 */
    private Long id;

    /** 上级菜单ID,顶级为 0 */
    private Long parentId;

    /** 菜单名称 */
    @NotBlank(message = "请输入菜单名称")
    @Size(max = 50, message = "菜单名称不能超过 50 个字符")
    private String menuName;

    /** 菜单类型:DIR-目录 MENU-菜单 BUTTON-按钮 */
    @NotBlank(message = "请选择菜单类型")
    private String menuType;

    /** 路由地址 */
    private String path;

    /** 前端组件路径(相对 src/views,如 admin/user/index) */
    private String component;

    /** 菜单图标 */
    private String icon;

    /** 权限标识 */
    private String perms;

    /** 排序 */
    private Integer sort;

    /** 是否显示:1-显示 0-隐藏 */
    private Integer visible;

    /** 状态:1-启用 0-禁用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
