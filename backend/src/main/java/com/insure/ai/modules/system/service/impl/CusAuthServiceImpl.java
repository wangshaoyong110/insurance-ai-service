package com.insure.ai.modules.system.service.impl;

import com.insure.ai.modules.system.service.SysLoginLogService;
import com.insure.ai.modules.system.service.CusAuthService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.CertTypeEnum;
import com.insure.ai.common.enums.CommonStatusEnum;
import com.insure.ai.common.enums.SmsSceneEnum;
import com.insure.ai.common.enums.UserTypeEnum;
import com.insure.ai.common.util.CertNoUtils;
import com.insure.ai.modules.system.dto.ChangePasswordDTO;
import com.insure.ai.modules.system.dto.CusProfileUpdateDTO;
import com.insure.ai.modules.system.dto.CusUserVO;
import com.insure.ai.modules.system.dto.LoginDTO;
import com.insure.ai.modules.system.dto.LoginVO;
import com.insure.ai.modules.system.dto.RealNameAuthDTO;
import com.insure.ai.modules.system.dto.RegisterDTO;
import com.insure.ai.modules.system.service.SmsService;
import com.insure.ai.modules.system.service.CaptchaService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Locale;

/**
 * 客户端认证服务(投保咨询客户)
 * <p>与后台管理账号完全隔离:查询 cus_user 表、签发 CUSTOMER 类型 token</p>
 *
 * @author insurance-ai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CusAuthServiceImpl implements CusAuthService {

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
    private final SmsService smsService;
    private final CaptchaService captchaService;

    /**
     * 客户端登录:支持手机号或账号
     *
     * @param dto     登录参数
     * @param request 当前请求(记录登录日志)
     * @return token 与客户信息
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public LoginVO<CusUserVO> login(LoginDTO dto, HttpServletRequest request) {
        // 图形验证码先校验且不增加登录失败计数:否则可用错误验证码把他人账号锁死
        captchaService.verifyForLogin(dto.getCaptchaId(), dto.getCaptchaCode());
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
    @Override
    public Long register(RegisterDTO dto) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "两次输入的密码不一致");
        }
        String phone = dto.getPhone().trim();
        // 复核发短信时用过的同一张图形验证码:要求已验证且与手机号绑定,随后一次性消费
        captchaService.verifyForRegister(dto.getCaptchaId(), dto.getCaptchaCode(), phone,
                smsService.isRegisterVerifyRequired());
        // 注册必须先通过短信验证码校验(可通过 insure.sms.verify-register 关闭,仅限本地联调)
        if (smsService.isRegisterVerifyRequired()) {
            smsService.verifyCode(phone, dto.getSmsCode(), SmsSceneEnum.REGISTER);
        }
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
     * 获取当前客户信息
     */
    @Override
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
    @Override
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
     * 更新个人资料
     * <p>
     * 只更新提交的非空字段(MyBatis-Plus 默认忽略 null)。两条硬规则:
     * 已实名的客户不允许自行改证件(证件是身份依据,改了就等于绕过核验);
     * 证件号只存明文、出口打码,格式不合法直接拒绝。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public CusUserVO updateProfile(Long cusUserId, CusProfileUpdateDTO dto) {
        CusUser exists = cusUserMapper.selectById(cusUserId);
        if (exists == null) {
            throw new BusinessException(ResultCode.CUS_USER_NOT_EXIST);
        }
        boolean verified = exists.getRealVerified() != null && exists.getRealVerified() == 1;
        boolean certProvided = StringUtils.hasText(dto.getCertType()) || StringUtils.hasText(dto.getCertNo());
        if (verified && certProvided) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(),
                    "已完成实名认证,证件信息不可自行修改,如需变更请联系客服");
        }

        CusUser update = new CusUser();
        update.setId(cusUserId);
        if (StringUtils.hasText(dto.getNickname())) {
            update.setNickname(dto.getNickname().trim());
        }
        if (StringUtils.hasText(dto.getEmail())) {
            update.setEmail(dto.getEmail().trim());
        }
        if (StringUtils.hasText(dto.getAvatar())) {
            update.setAvatar(dto.getAvatar().trim());
        }
        if (StringUtils.hasText(dto.getRealName())) {
            update.setRealName(dto.getRealName().trim());
        }
        if (dto.getGender() != null) {
            if (dto.getGender() < 0 || dto.getGender() > 2) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "性别取值不正确");
            }
            update.setGender(dto.getGender());
        }
        if (dto.getBirthday() != null) {
            if (dto.getBirthday().isAfter(LocalDate.now())) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "出生日期不能晚于今天");
            }
            update.setBirthday(dto.getBirthday());
        }
        if (certProvided) {
            CertTypeEnum type = CertTypeEnum.parse(dto.getCertType())
                    .orElseThrow(() -> new BusinessException(ResultCode.CERT_TYPE_NOT_ALLOWED));
            String certNo = dto.getCertNo() == null ? "" : dto.getCertNo().trim().toUpperCase(Locale.ROOT);
            if (!CertNoUtils.isValid(type.getCode(), certNo)) {
                throw new BusinessException(ResultCode.CERT_NO_FORMAT_ERROR);
            }
            update.setCertType(type.getCode());
            update.setCertNo(certNo);
        }
        cusUserMapper.updateById(update);
        return CusUserVO.from(cusUserMapper.selectById(cusUserId));
    }

    /**
     * 实名认证
     * <p>
     * 校验姓名与证件格式 → 二要素核验(通道未接入时仅本地校验)→ 置为已实名。
     * 身份证还能顺带补全生日与性别,减少用户重复填写。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public CusUserVO realNameAuth(Long cusUserId, RealNameAuthDTO dto) {
        CusUser exists = cusUserMapper.selectById(cusUserId);
        if (exists == null) {
            throw new BusinessException(ResultCode.CUS_USER_NOT_EXIST);
        }
        CertTypeEnum type = CertTypeEnum.parse(dto.getCertType())
                .orElseThrow(() -> new BusinessException(ResultCode.CERT_TYPE_NOT_ALLOWED));
        String certNo = dto.getCertNo().trim().toUpperCase(Locale.ROOT);
        if (!CertNoUtils.isValid(type.getCode(), certNo)) {
            throw new BusinessException(ResultCode.CERT_NO_FORMAT_ERROR);
        }
        String realName = dto.getRealName().trim();
        if (realName.length() < 2) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请填写证件上的真实姓名");
        }
        doRealNameVerify(realName, type, certNo);

        CusUser update = new CusUser();
        update.setId(cusUserId);
        update.setRealName(realName);
        update.setCertType(type.getCode());
        update.setCertNo(certNo);
        update.setRealVerified(1);
        update.setRealVerifiedTime(LocalDateTime.now());
        // 身份证号里带出生日期与性别,用户还没填就顺带补上,省一次手工输入
        if (type == CertTypeEnum.ID_CARD) {
            if (exists.getBirthday() == null) {
                update.setBirthday(LocalDate.of(
                        Integer.parseInt(certNo.substring(6, 10)),
                        Integer.parseInt(certNo.substring(10, 12)),
                        Integer.parseInt(certNo.substring(12, 14))));
            }
            if (exists.getGender() == null || exists.getGender() == 0) {
                // 第 17 位奇数为男、偶数为女
                update.setGender((certNo.charAt(16) - '0') % 2 == 1 ? 1 : 2);
            }
        }
        cusUserMapper.updateById(update);
        log.info("[实名认证] 客户 {} 认证通过 name={} certType={} certNo={}",
                cusUserId, realName, type.getCode(), CertNoUtils.mask(certNo));
        return CusUserVO.from(cusUserMapper.selectById(cusUserId));
    }

    /**
     * 二要素核验(姓名 + 证件号)
     * <p>
     * TODO 对接公安/运营商实名核验通道:目前仅做本地格式校验(已含身份证校验位),
     * 接入后在下方发起 HTTP 调用,核验不通过抛 {@link ResultCode#REAL_NAME_INFO_MISMATCH}。
     * </p>
     */
    private void doRealNameVerify(String realName, CertTypeEnum type, String certNo) {
        log.info("[实名认证] 本地校验通过,核验通道未接入 name={} certType={} certNo={}",
                realName, type.getCode(), CertNoUtils.mask(certNo));
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
