package com.insure.ai.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.insure.ai.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户端用户实体(投保咨询客户)
 * <p>对应表 cus_user,与后台管理账号 sys_user 是两套独立的用户体系</p>
 *
 * @author insurance-ai
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cus_user")
public class CusUser extends BaseEntity {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号(可为空,默认手机号登录) */
    private String username;

    /** 登录密码(BCrypt 密文) */
    @JsonIgnore
    private String password;

    /** 昵称 */
    private String nickname;

    /** 真实姓名 */
    private String realName;

    /** 证件类型:见 CertTypeEnum */
    private String certType;

    /** 证件号(出口一律打码,见 CertNoUtils.mask) */
    private String certNo;

    /** 手机号(全局唯一,支持手机号登录) */
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

    /** 状态:1-启用 0-禁用 */
    private Integer status;

    /** 注册来源:H5-手机端 */
    private String registerSource;

    /** 最后登录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginTime;
}
