package com.insure.ai.modules.system.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录成功返回对象
 * <p>使用泛型区分两端用户信息:管理端为 {@link UserVO},客户端为 {@link CusUserVO}</p>
 *
 * @param <T> 用户信息类型
 * @author insurance-ai
 */
@Data
@NoArgsConstructor
public class LoginVO<T> implements Serializable {

    /** JWT 令牌 */
    private String token;
    /** 令牌前缀,前端拼接为 Authorization: Bearer xxx */
    private String tokenPrefix;
    /** 有效期(秒) */
    private Long expiresIn;
    /** 用户信息 */
    private T userInfo;

    public static <T> LoginVO<T> of(String token, String tokenPrefix, Long expiresIn, T userInfo) {
        LoginVO<T> vo = new LoginVO<>();
        vo.setToken(token);
        vo.setTokenPrefix(tokenPrefix);
        vo.setExpiresIn(expiresIn);
        vo.setUserInfo(userInfo);
        return vo;
    }
}
