package com.insure.ai.modules.system.service;

import com.insure.ai.modules.system.dto.ChangePasswordDTO;
import com.insure.ai.modules.system.dto.LoginDTO;
import com.insure.ai.modules.system.dto.LoginVO;
import com.insure.ai.modules.system.dto.UserVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 管理端认证服务(业务操作人员)
 * <p>与客户端认证完全独立:查询 sys_user 表、签发 ADMIN 类型 token</p>
 *
 * @author insurance-ai
 */
public interface AdminAuthService {

    /**
     * 管理端登录:支持账号或手机号
     *
     * @param dto     登录参数
     * @param request 当前请求(记录登录日志)
     * @return token 与管理员信息
     */
    LoginVO<UserVO> login(LoginDTO dto, HttpServletRequest request);

    /**
     * 退出登录:token 加入黑名单
     */
    void logout(String token);

    /**
     * 获取当前管理员信息
     */
    UserVO currentUser(Long userId);

    /**
     * 修改密码
     */
    void changePassword(Long userId, ChangePasswordDTO dto);

    /**
     * 当前登录管理员的角色标识(用于菜单查询)
     */
    String currentRoleCode(Long userId);

    /**
     * 当前登录管理员权限校验:仅超级管理员可操作敏感资源
     */
    boolean isSuperAdmin(Long userId);
}
