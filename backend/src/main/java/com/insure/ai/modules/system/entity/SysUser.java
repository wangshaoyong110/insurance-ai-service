package com.insure.ai.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.insure.ai.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 管理端用户实体(业务操作人员)
 * <p>对应表 sys_user,与客户端客户表 cus_user 完全隔离</p>
 *
 * @author insurance-ai
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号 */
    private String username;

    /** 登录密码(BCrypt 密文,不返回给前端) */
    @JsonIgnore
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 手机号(管理端内唯一) */
    private String phone;

    /** 电子邮箱 */
    private String email;

    /** 头像地址 */
    private String avatar;

    /** 角色ID(关联 sys_role.id) */
    private Long roleId;

    /** 状态:1-启用 0-禁用 */
    private Integer status;

    /** 最后登录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginTime;

    /** 角色名称(非数据库字段,列表展示用) */
    @TableField(exist = false)
    private String roleName;

    /** 角色标识(非数据库字段) */
    @TableField(exist = false)
    private String roleCode;
}
