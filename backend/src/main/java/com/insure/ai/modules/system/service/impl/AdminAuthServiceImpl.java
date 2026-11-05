package com.insure.ai.modules.system.service.impl;

import com.insure.ai.modules.system.service.SysLoginLogService;
import com.insure.ai.modules.system.service.AdminAuthService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.Constants;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.CommonStatusEnum;
import com.insure.ai.common.enums.UserTypeEnum;
import com.insure.ai.modules.system.dto.ChangePasswordDTO;
import com.insure.ai.modules.system.dto.LoginDTO;
import com.insure.ai.modules.system.dto.LoginVO;
import com.insure.ai.modules.system.dto.UserVO;
import com.insure.ai.modules.system.entity.SysRole;
import com.insure.ai.modules.system.entity.SysUser;
import com.insure.ai.modules.system.mapper.SysRoleMapper;
import com.insure.ai.modules.system.mapper.SysUserMapper;
import com.insure.ai.security.JwtUtils;
import com.insure.ai.security.LoginAttemptService;
import com.insure.ai.security.LoginUser;
import com.insure.ai.security.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 管理端认证服务(业务操作人员)
 * <p>与客户端认证完全独立:查询 sys_user 表、签发 ADMIN 类型 token</p>
 *
 * @author insurance-ai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

    /** 登录失败计数作用域 */
    private static final String LOGIN_SCOPE = "admin";
    /** 毫秒转秒 */
    private static final long MILLIS_TO_SECONDS = 1000L;

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysLoginLogService sysLoginLogService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttemptService;

    /**
     * 管理端登录:支持账号或手机号
     *
     * @param dto     登录参数
     * @param request 当前请求(记录登录日志)
     * @return token 与管理员信息
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public LoginVO<UserVO> login(LoginDTO dto, HttpServletRequest request) {
        String account = dto.getAccount().trim();
        loginAttemptService.checkLimit(LOGIN_SCOPE, account);
        SysUser user = sysUserMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                // 账号或手机号登录,用括号包裹保证逻辑删除条件不被 OR 破坏
                .and(wrapper -> wrapper.eq(SysUser::getUsername, account)
                        .or()
                        .eq(SysUser::getPhone, account))
                .last("LIMIT 1"));
        if (user == null) {
            sysLoginLogService.record(null, account, UserTypeEnum.ADMIN, request, false, "账号不存在");
            loginAttemptService.increase(LOGIN_SCOPE, account);
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            sysLoginLogService.record(user.getId(), account, UserTypeEnum.ADMIN, request, false, "密码错误");
            loginAttemptService.increase(LOGIN_SCOPE, account);
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }
        if (!CommonStatusEnum.ENABLE.getCode().equals(user.getStatus())) {
            sysLoginLogService.record(user.getId(), account, UserTypeEnum.ADMIN, request, false, "账号已被禁用");
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        // 角色信息用于生成权限与菜单
        SysRole role = user.getRoleId() == null ? null : sysRoleMapper.selectById(user.getRoleId());
        if (role == null || !CommonStatusEnum.ENABLE.getCode().equals(role.getStatus())) {
            sysLoginLogService.record(user.getId(), account, UserTypeEnum.ADMIN, request, false, "角色不存在或已停用");
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "账号未分配有效角色,请联系管理员");
        }
        user.setRoleName(role.getRoleName());
        user.setRoleCode(role.getRoleCode());

        // 登录成功:清空失败计数、更新最后登录时间
        loginAttemptService.clear(LOGIN_SCOPE, account);
        updateLastLoginTime(user.getId());

        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getPassword(),
                user.getRealName(), user.getPhone(), UserTypeEnum.ADMIN.getCode(),
                role.getRoleCode(), user.getStatus());
        String token = jwtUtils.generateToken(loginUser);
        sysLoginLogService.record(user.getId(), user.getUsername(), UserTypeEnum.ADMIN, request, true, "登录成功");
        log.info("[管理端登录] 账号={} 角色={}", user.getUsername(), role.getRoleCode());
        return LoginVO.of(token, "Bearer", jwtUtils.getExpire() / MILLIS_TO_SECONDS, UserVO.from(user));
    }

    /**
     * 退出登录:token 加入黑名单
     */
    @Override
    public void logout(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        Date expiration = jwtUtils.getExpiration(token);
        long ttlSeconds = expiration == null ? 0
                : (expiration.getTime() - System.currentTimeMillis()) / MILLIS_TO_SECONDS;
        tokenService.addBlacklist(token, ttlSeconds);
    }

    /**
     * 获取当前管理员信息
     */
    @Override
    public UserVO currentUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        fillRole(user);
        return UserVO.from(user);
    }

    /**
     * 修改密码
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void changePassword(Long userId, ChangePasswordDTO dto) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.USER_OLD_PASSWORD_ERROR);
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        sysUserMapper.updateById(update);
    }

    /**
     * 更新最后登录时间
     */
    private void updateLastLoginTime(Long userId) {
        SysUser update = new SysUser();
        update.setId(userId);
        update.setLastLoginTime(LocalDateTime.now());
        sysUserMapper.updateById(update);
    }

    /**
     * 填充角色信息
     */
    private void fillRole(SysUser user) {
        if (user.getRoleId() == null) {
            return;
        }
        SysRole role = sysRoleMapper.selectById(user.getRoleId());
        if (role != null) {
            user.setRoleName(role.getRoleName());
            user.setRoleCode(role.getRoleCode());
        }
    }

    /**
     * 当前登录管理员的角色标识(用于菜单查询)
     */
    @Override
    public String currentRoleCode(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getRoleId() == null) {
            return Constants.ROLE_OPERATOR;
        }
        SysRole role = sysRoleMapper.selectById(user.getRoleId());
        return role == null ? Constants.ROLE_OPERATOR : role.getRoleCode();
    }

    /**
     * 当前登录管理员权限校验:仅超级管理员可操作敏感资源
     */
    @Override
    public boolean isSuperAdmin(Long userId) {
        return Constants.ROLE_SUPER_ADMIN.equals(currentRoleCode(userId));
    }
}
