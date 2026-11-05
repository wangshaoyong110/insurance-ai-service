package com.insure.ai.modules.system.service;

import com.insure.ai.modules.system.dto.ChangePasswordDTO;
import com.insure.ai.modules.system.dto.CusUserVO;
import com.insure.ai.modules.system.dto.LoginDTO;
import com.insure.ai.modules.system.dto.LoginVO;
import com.insure.ai.modules.system.dto.RegisterDTO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端认证服务(投保咨询客户)
 * <p>与后台管理账号完全隔离:查询 cus_user 表、签发 CUSTOMER 类型 token</p>
 *
 * @author insurance-ai
 */
public interface CusAuthService {

    /**
     * 客户端登录:支持手机号或账号
     *
     * @param dto     登录参数
     * @param request 当前请求(记录登录日志)
     * @return token 与客户信息
     */
    LoginVO<CusUserVO> login(LoginDTO dto, HttpServletRequest request);

    /**
     * 客户注册:手机号必须唯一
     */
    Long register(RegisterDTO dto);

    /**
     * 退出登录:token 加入黑名单
     */
    void logout(String token);

    /**
     * 获取当前客户信息
     */
    CusUserVO currentUser(Long cusUserId);

    /**
     * 修改密码
     */
    void changePassword(Long cusUserId, ChangePasswordDTO dto);

    /**
     * 更新个人资料(仅允许修改昵称与邮箱,手机号需通过客服变更)
     */
    CusUserVO updateProfile(Long cusUserId, String nickname, String email);
}
