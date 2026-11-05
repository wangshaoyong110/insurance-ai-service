package com.insure.ai.modules.system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.insure.ai.common.enums.CertTypeEnum;
import com.insure.ai.common.enums.UserTypeEnum;
import com.insure.ai.common.util.CertNoUtils;
import com.insure.ai.modules.system.entity.CusUser;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
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
    /** 证件类型编码 */
    private String certType;
    /** 证件类型描述 */
    private String certTypeDesc;
    /** 证件号(打码,只保留前 3 后 2) */
    private String certNo;
    /** 手机号 */
    private String phone;
    /** 电子邮箱 */
    private String email;
    /** 头像地址 */
    private String avatar;
    /** 性别:0-未知 1-男 2-女 */
    private Integer gender;
    /** 出生日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;
    /** 实名认证:0-未认证 1-已认证 */
    private Integer realVerified;
    /** 实名认证时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime realVerifiedTime;
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
     * <p>证件号出口一律打码,前端编辑资料时展示打码值,改动证件号必须整串重新输入</p>
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
        vo.setCertType(user.getCertType());
        vo.setCertTypeDesc(CertTypeEnum.descOf(user.getCertType()));
        vo.setCertNo(CertNoUtils.mask(user.getCertNo()));
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setGender(user.getGender());
        vo.setBirthday(user.getBirthday());
        vo.setRealVerified(user.getRealVerified() == null ? 0 : user.getRealVerified());
        vo.setRealVerifiedTime(user.getRealVerifiedTime());
        vo.setStatus(user.getStatus());
        vo.setRegisterSource(user.getRegisterSource());
        vo.setCreateTime(user.getCreateTime());
        vo.setLastLoginTime(user.getLastLoginTime());
        return vo;
    }
}
