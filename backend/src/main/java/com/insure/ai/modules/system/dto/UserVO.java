package com.insure.ai.modules.system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.insure.ai.common.enums.UserTypeEnum;
import com.insure.ai.modules.system.entity.SysUser;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理端用户信息返回对象(不含密码等敏感字段)
 *
 * @author insurance-ai
 */
@Data
public class UserVO implements Serializable {

    /** 用户ID */
    private Long id;
    /** 登录账号 */
    private String username;
    /** 真实姓名 */
    private String realName;
    /** 手机号 */
    private String phone;
    /** 电子邮箱 */
    private String email;
    /** 头像地址 */
    private String avatar;
    /** 端类型:固定为 ADMIN */
    private String userType = UserTypeEnum.ADMIN.getCode();
    /** 端类型描述 */
    private String userTypeDesc = UserTypeEnum.ADMIN.getDesc();
    /** 角色ID */
    private Long roleId;
    /** 角色名称 */
    private String roleName;
    /** 角色标识 */
    private String roleCode;
    /** 状态:1-启用 0-禁用 */
    private Integer status;
    /** 备注 */
    private String remark;
    /** 创建者 */
    private String createBy;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    /** 最后登录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginTime;

    /**
     * 实体转 VO
     */
    public static UserVO from(SysUser user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setRoleId(user.getRoleId());
        vo.setRoleName(user.getRoleName());
        vo.setRoleCode(user.getRoleCode());
        vo.setStatus(user.getStatus());
        vo.setRemark(user.getRemark());
        vo.setCreateBy(user.getCreateBy());
        vo.setCreateTime(user.getCreateTime());
        vo.setLastLoginTime(user.getLastLoginTime());
        return vo;
    }
}
