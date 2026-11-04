package com.insure.ai.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.insure.ai.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理端菜单实体
 * <p>菜单类型:DIR-目录 MENU-菜单 BUTTON-按钮</p>
 *
 * @author insurance-ai
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父级菜单ID(0 表示顶级) */
    private Long parentId;

    /** 菜单名称 */
    private String menuName;

    /** 菜单类型:DIR-目录 MENU-菜单 BUTTON-按钮 */
    private String menuType;

    /** 路由地址 */
    private String path;

    /** 前端组件路径(相对 src/views,如 admin/user/index) */
    private String component;

    /** 菜单图标(Element Plus 图标名) */
    private String icon;

    /** 权限标识 */
    private String perms;

    /** 排序(升序) */
    private Integer sort;

    /** 是否显示:1-显示 0-隐藏 */
    private Integer visible;

    /** 状态:1-启用 0-禁用 */
    private Integer status;

    /** 子菜单(非数据库字段,构建树时使用) */
    @TableField(exist = false)
    private List<SysMenu> children = new ArrayList<>();
}
