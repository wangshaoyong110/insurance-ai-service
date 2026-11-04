package com.insure.ai.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.insure.ai.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端角色实体
 *
 * @author insurance-ai
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色标识,如 SUPER_ADMIN / OPERATOR */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 排序(升序) */
    private Integer sort;

    /** 状态:1-启用 0-禁用 */
    private Integer status;

    /** 关联用户数(非数据库字段) */
    @TableField(exist = false)
    private Long userCount;
}
