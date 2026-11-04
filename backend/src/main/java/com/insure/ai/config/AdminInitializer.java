package com.insure.ai.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.insure.ai.common.Constants;
import com.insure.ai.common.enums.CommonStatusEnum;
import com.insure.ai.modules.system.entity.SysUser;
import com.insure.ai.modules.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 超级管理员初始化器
 * <p>启动时校验内置 admin 账号是否存在,不存在则自动创建(密码 123456),避免首次部署无法登录后台</p>
 *
 * @author insurance-ai
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    /** 内置超管账号 */
    private static final String ADMIN_USERNAME = Constants.BUILT_IN_ADMIN;
    /** 内置超管初始密码 */
    private static final String ADMIN_PASSWORD = Constants.DEFAULT_PASSWORD;
    /** 内置超管手机号 */
    private static final String ADMIN_PHONE = "13800000000";
    /** 内置超管角色ID(见初始化脚本 sys_role) */
    private static final Long ADMIN_ROLE_ID = 1L;

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        Long count = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, ADMIN_USERNAME));
        if (count != null && count > 0) {
            return;
        }
        SysUser admin = new SysUser();
        admin.setUsername(ADMIN_USERNAME);
        admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setRealName("超级管理员");
        admin.setPhone(ADMIN_PHONE);
        admin.setEmail("admin@insure.com");
        admin.setRoleId(ADMIN_ROLE_ID);
        admin.setStatus(CommonStatusEnum.ENABLE.getCode());
        admin.setRemark("系统初始化自动创建");
        sysUserMapper.insert(admin);
        log.info("[初始化] 超级管理员账号已创建: {}", ADMIN_USERNAME);
    }
}
