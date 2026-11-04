package com.insure.ai.common.util;

import com.insure.ai.common.BusinessException;
import com.insure.ai.common.ResultCode;
import com.insure.ai.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具类
 *
 * @author insurance-ai
 */
public class SecurityUtils {

    /** 匿名操作时填充的操作人 */
    public static final String ANONYMOUS = "anonymous";
    /** 系统自动操作时填充的操作人 */
    public static final String SYSTEM = "system";

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户,未登录返回 null
     */
    public static LoginUser getLoginUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            return null;
        }
        return loginUser;
    }

    /**
     * 获取当前登录用户,未登录抛出 401 业务异常
     */
    public static LoginUser getLoginUser() {
        LoginUser loginUser = getLoginUserOrNull();
        if (loginUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return loginUser;
    }

    /**
     * 当前用户ID
     */
    public static Long getUserId() {
        return getLoginUser().getId();
    }

    /**
     * 当前登录账号
     */
    public static String getUsername() {
        LoginUser loginUser = getLoginUserOrNull();
        return loginUser == null ? ANONYMOUS : loginUser.getUsername();
    }

    /**
     * 当前登录账号(为空时使用 system,用于自动填充创建者)
     */
    public static String getUsernameOrSystem() {
        LoginUser loginUser = getLoginUserOrNull();
        return loginUser == null ? SYSTEM : loginUser.getUsername();
    }

    /**
     * 是否超级管理员
     */
    public static boolean isAdmin() {
        LoginUser loginUser = getLoginUserOrNull();
        return loginUser != null && com.insure.ai.common.enums.UserTypeEnum.ADMIN.getCode().equals(loginUser.getUserType());
    }
}
