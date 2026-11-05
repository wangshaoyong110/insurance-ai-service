package com.insure.ai.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 实名认证提交参数
 * <p>
 * 姓名须与证件号匹配。身份证做全格式校验(含校验位);
 * 对接公安/运营商二要素核验通道前的本地校验,通道接入点见
 * CusAuthServiceImpl#doRealNameVerify 中的 TODO。
 * </p>
 *
 * @author shaoyong.wang
 */
@Data
public class RealNameAuthDTO implements Serializable {

    /** 真实姓名 */
    @NotBlank(message = "请填写真实姓名")
    @Size(max = 50, message = "姓名不能超过 50 字")
    private String realName;

    /** 证件类型:见 CertTypeEnum */
    @NotBlank(message = "请选择证件类型")
    private String certType;

    /** 证件号 */
    @NotBlank(message = "请填写证件号")
    @Size(max = 64, message = "证件号过长")
    private String certNo;
}
