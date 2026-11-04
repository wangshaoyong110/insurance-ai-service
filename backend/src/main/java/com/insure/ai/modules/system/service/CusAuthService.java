package com.insure.ai.modules.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.CommonStatusEnum;
import com.insure.ai.common.enums.UserTypeEnum;
import com.insure.ai.modules.system.dto.ChangePasswordDTO;
import com.insure.ai.modules.system.dto.CusUserVO;
import com.insure.ai.modules.system.dto.LoginDTO;
import com.insure.ai.modules.system.dto.LoginVO;
import com.insure.ai.modules.system.dto.RegisterDTO;
import com.insure.ai.modules.system.entity.CusUser;
import com.insure.ai.modules.system.mapper.CusUserMapper;
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
 * 客户端认证服务(投保咨询客户)
 * <p>与后台管理账号完全隔离:查询 cus_user 表、签发 CUSTOMER 类型 token</p>
 *
 * @author insurance-ai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CusAuthService {

    /** 登录失败计数作用域 */
    private static final String LOGIN_SCOPE = "customer";
    /** 毫秒转秒 */
    private static final long MILLIS_TO_SECONDS = 1000L;
    /** 默认注册来源 */
    private static final String SOURCE_H5 = "H5";

    private final CusUserMapper cusUserMapper;
    private final SysLoginLogService sysLoginLogService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttemptService;

    /**
     * 客户端登录:支持手机号或账号
     *
     * @param dto     登录参数
     * @param request 当前请求(记录登录日志)
     * @return token 与客户信息
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO<CusUserVO> login(LoginDTO dto, HttpServletRequest request) {
        String account = dto.getAccount().trim();
        loginAttemptService.checkLimit(LOGIN_SCOPE, account);
        CusUser user = cusUserMapper.selectOne(Wrappers.<CusUser>lambdaQuery()
                .and(wrapper -> wrapper.eq(CusUser::getPhone, account)
                        .or()
                        .eq(CusUser::getUsername, account))
                .last("LIMIT 1"));
        if (user == null) {
            sysLoginLogService.record(null, account, UserTypeEnum.CUSTOMER, request, false, "账号不存在");
            loginAttemptService.increase(LOGIN_SCOPE, account);
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            sysLoginLogService.record(user.getId(), account, UserTypeEnum.CUSTOMER, request, false, "密码错误");
            loginAttemptService.increase(LOGIN_SCOPE, account);
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }
        if (!CommonStatusEnum.ENABLE.getCode().equals(user.getStatus())) {
            sysLoginLogService.record(user.getId(), account, UserTypeEnum.CUSTOMER, request, false, "账号已被禁用");
            throw new BusinessException(ResultCode.CUS_USER_DISABLED);
        }
        loginAttemptService.clear(LOGIN_SCOPE, account);
        CusUser update = new CusUser();
        update.setId(user.getId());
        update.setLastLoginTime(LocalDateTime.now());
        cusUserMapper.updateById(update);
        user.setLastLoginTime(update.getLastLoginTime());

        // 客户端不携带角色标识,权限仅为 ROLE_CUSTOMER
        LoginUser loginUser = new LoginUser(user.getId(), buildDisplayAccount(user), user.getPassword(),
                buildDisplayName(user), user.getPhone(), UserTypeEnum.CUSTOMER.getCode(),
                null, user.getStatus());
        String token = jwtUtils.generateToken(loginUser);
        sysLoginLogService.record(user.getId(), buildDisplayAccount(user), UserTypeEnum.CUSTOMER,
                request, true, "登录成功");
        log.info("[客户端登录] 客户ID={} 手机号={}", user.getId(), user.getPhone());
        return LoginVO.of(token, "Bearer", jwtUtils.getExpire() / MILLIS_TO_SECONDS, CusUserVO.from(user));
    }

    /**
     * 客户注册:手机号必须唯一
     */
    @Transactional(rollbackFor = Exception.class)
    public Long register(RegisterDTO dto) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "两次输入的密码不一致");
        }
        String phone = dto.getPhone().trim();
        // 手机号唯一性校验(逻辑未删除范围内)
        Long phoneCount = cusUserMapper.selectCount(Wrappers.<CusUser>lambdaQuery()
                .eq(CusUser::getPhone, phone));
        if (phoneCount > 0) {
            throw new BusinessException(ResultCode.USER_PHONE_EXIST);
        }
        // 账号可选,填写时同样要求唯一
        String username = StringUtils.hasText(dto.getUsername()) ? dto.getUsername().trim() : null;
        if (username != null) {
            Long usernameCount = cusUserMapper.selectCount(Wrappers.<CusUser>lambdaQuery()
                    .eq(CusUser::getUsername, username));
            if (usernameCount > 0) {
                throw new BusinessException(ResultCode.USER_NAME_EXIST);
            }
        }
        CusUser user = new CusUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setPhone(phone);
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname()
                : (StringUtils.hasText(dto.getRealName()) ? dto.getRealName() : "客户" + phone.substring(phone.length() - 4)));
        user.setRealName(dto.getRealName());
        user.setEmail(dto.getEmail());
        user.setGender(0);
        user.setStatus(CommonStatusEnum.ENABLE.getCode());
        user.setRegisterSource(SOURCE_H5);
        user.setRemark("客户端自主注册");
        cusUserMapper.insert(user);
        log.info("[客户端注册] 客户ID={} 手机号={}", user.getId(), phone);
        return user.getId();
    }

    /**
     * 退出登录:token 加入黑名单
     */
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
     * 获取当前客户信息
     */
    public CusUserVO currentUser(Long cusUserId) {
        CusUser user = cusUserMapper.selectById(cusUserId);
        if (user == null) {
            throw new BusinessException(ResultCode.CUS_USER_NOT_EXIST);
        }
        return CusUserVO.from(user);
    }

    /**
     * 修改密码
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long cusUserId, ChangePasswordDTO dto) {
        CusUser user = cusUserMapper.selectById(cusUserId);
        if (user == null) {
            throw new BusinessException(ResultCode.CUS_USER_NOT_EXIST);
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.USER_OLD_PASSWORD_ERROR);
        }
        CusUser update = new CusUser();
        update.setId(cusUserId);
        update.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        cusUserMapper.updateById(update);
    }

    /**
     * 更新个人资料(仅允许修改昵称与邮箱,手机号需通过客服变更)
     */
    @Transactional(rollbackFor = Exception.class)
    public CusUserVO updateProfile(Long cusUserId, String nickname, String email) {
        CusUser exists = cusUserMapper.selectById(cusUserId);
        if (exists == null) {
            throw new BusinessException(ResultCode.CUS_USER_NOT_EXIST);
        }
        CusUser update = new CusUser();
        update.setId(cusUserId);
        update.setNickname(StringUtils.hasText(nickname) ? nickname.trim() : exists.getNickname());
        update.setEmail(StringUtils.hasText(email) ? email.trim() : exists.getEmail());
        cusUserMapper.updateById(update);
        return CusUserVO.from(cusUserMapper.selectById(cusUserId));
    }

    /**
     * 登录账号展示值:未设置账号时使用手机号
     */
    private String buildDisplayAccount(CusUser user) {
        return StringUtils.hasText(user.getUsername()) ? user.getUsername() : user.getPhone();
    }

    /**
     * 展示名称:昵称 > 姓名 > 手机号后四位
     */
    private String buildDisplayName(CusUser user) {
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        if (StringUtils.hasText(user.getRealName())) {
            return user.getRealName();
        }
        String phone = user.getPhone();
        return "客户" + (phone.length() > 4 ? phone.substring(phone.length() - 4) : phone);
    }
}
