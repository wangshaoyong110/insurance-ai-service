package com.insure.ai.modules.system.service;

import com.insure.ai.modules.system.dto.ChangePasswordDTO;
import com.insure.ai.modules.system.dto.CusProfileUpdateDTO;
import com.insure.ai.modules.system.dto.CusUserVO;
import com.insure.ai.modules.system.dto.LoginDTO;
import com.insure.ai.modules.system.dto.LoginVO;
import com.insure.ai.modules.system.dto.RealNameAuthDTO;
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
     * 更新个人资料:昵称 / 邮箱 / 头像 / 性别 / 生日 / 姓名 / 证件信息
     * <p>手机号是登录凭证,不允许自行修改;证件号只做格式校验,不出现在返回的明文里</p>
     */
    CusUserVO updateProfile(Long cusUserId, CusProfileUpdateDTO dto);

    /**
     * 实名认证:姓名 + 证件类型 + 证件号
     * <p>校验证件格式后把客户标记为已实名;认证成功后查保单/理赔不再受限。
     * 真实二要素核验通道未接入时由 mock 开关控制(见实现类 TODO)。</p>
     */
    CusUserVO realNameAuth(Long cusUserId, RealNameAuthDTO dto);
}
