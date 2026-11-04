package com.insure.ai.modules.system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.insure.ai.common.enums.UserTypeEnum;
import com.insure.ai.modules.system.entity.CusUser;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户端用户信息返回对象
 *
 * @author insurance-ai
 */
@Data
public class CusUserVO implements Serializable {

    /** 客户ID */
    private Long id;
    /** 登录账号 */
    private String username;
    /** 昵称 */
    private String nickname;
    /** 真实姓名 */
    private String realName;
    /** 手机号 */
    private String phone;
    /** 电子邮箱 */
    private String email;
    /** 头像地址 */
    private String avatar;
    /** 性别:0-未知 1-男 2-女 */
    private Integer gender;
    /** 端类型:固定为 CUSTOMER */
    private String userType = UserTypeEnum.CUSTOMER.getCode();
    /** 端类型描述 */
    private String userTypeDesc = UserTypeEnum.CUSTOMER.getDesc();
    /** 状态:1-启用 0-禁用 */
    private Integer status;
    /** 注册来源 */
    private String registerSource;
    /** 注册时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    /** 最后登录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginTime;
    /** 会话数量(后台列表统计用) */
    private Long sessionCount;

    /**
     * 实体转 VO
     */
    public static CusUserVO from(CusUser user) {
        if (user == null) {
            return null;
        }
        CusUserVO vo = new CusUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setGender(user.getGender());
        vo.setStatus(user.getStatus());
        vo.setRegisterSource(user.getRegisterSource());
        vo.setCreateTime(user.getCreateTime());
        vo.setLastLoginTime(user.getLastLoginTime());
        return vo;
    }
}
