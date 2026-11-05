package com.insure.ai.modules.system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 客户资料修改参数
 * <p>
 * 所有字段可选,只更新提交的非空字段。证件号仅做格式校验,
 * 真实有效性由实名认证接口保证。
 * </p>
 *
 * @author shaoyong.wang
 */
@Data
public class CusProfileUpdateDTO implements Serializable {

    /** 昵称 */
    @Size(max = 20, message = "昵称不能超过 20 字")
    private String nickname;

    /** 真实姓名 */
    @Size(max = 50, message = "姓名不能超过 50 字")
    private String realName;

    /** 电子邮箱 */
    @Size(max = 100, message = "邮箱过长")
    private String email;

    /** 头像地址(先调 /file/upload?bizType=avatar 上传拿到 url) */
    @Size(max = 255, message = "头像地址过长")
    private String avatar;

    /** 性别:0-未知 1-男 2-女 */
    private Integer gender;

    /** 出生日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;

    /** 证件类型:见 CertTypeEnum */
    @Size(max = 20, message = "证件类型不正确")
    private String certType;

    /** 证件号 */
    @Size(max = 64, message = "证件号过长")
    private String certNo;
}
