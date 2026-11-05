package com.insure.ai.modules.system.service.impl;

import com.insure.ai.modules.system.service.SysLoginLogService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.insure.ai.common.PageResult;
import com.insure.ai.common.enums.CommonStatusEnum;
import com.insure.ai.common.enums.UserTypeEnum;
import com.insure.ai.common.util.DateUtils;
import com.insure.ai.common.util.WebUtils;
import com.insure.ai.modules.system.dto.LogQuery;
import com.insure.ai.modules.system.entity.SysLoginLog;
import com.insure.ai.modules.system.mapper.SysLoginLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 登录日志服务:写入 + 分页查询
 *
 * @author insurance-ai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysLoginLogServiceImpl implements SysLoginLogService {

    private final SysLoginLogMapper sysLoginLogMapper;

    /**
     * 记录登录日志
     * <p>日志写入失败不能影响主流程,因此内部吞掉异常并打印告警</p>
     *
     * @param userId    用户ID(登录失败时可为空)
     * @param username  登录账号
     * @param loginType 登录端:ADMIN-管理端 CUSTOMER-客户端
     * @param request   当前请求(用于提取 IP、浏览器、操作系统)
     * @param success   是否登录成功
     * @param msg       提示信息
     */
    @Override
    public void record(Long userId, String username, UserTypeEnum loginType,
                       HttpServletRequest request, boolean success, String msg) {
        try {
            SysLoginLog logEntity = new SysLoginLog();
            logEntity.setUserId(userId);
            logEntity.setUsername(username);
            logEntity.setLoginType(loginType.getCode());
            logEntity.setIp(WebUtils.getIp(request));
            logEntity.setBrowser(WebUtils.getBrowser(request));
            logEntity.setOs(WebUtils.getOs(request));
            logEntity.setStatus(success ? CommonStatusEnum.ENABLE.getCode() : CommonStatusEnum.DISABLE.getCode());
            logEntity.setMsg(msg);
            logEntity.setLoginTime(LocalDateTime.now());
            sysLoginLogMapper.insert(logEntity);
        } catch (Exception e) {
            log.warn("[登录日志] 写入失败,account={}, reason={}", username, e.getMessage());
        }
    }

    /**
     * 分页查询登录日志
     */
    @Override
    public PageResult<SysLoginLog> page(LogQuery query, UserTypeEnum loginType) {
        LocalDateTime start = DateUtils.parseStartOfDay(query.getStartDate());
        LocalDateTime end = DateUtils.parseEndOfDay(query.getEndDate());
        Page<SysLoginLog> page = new Page<>(query.getPageNum(), query.getPageSize());
        sysLoginLogMapper.selectPage(page, Wrappers.<SysLoginLog>lambdaQuery()
                .like(StringUtils.hasText(query.getKeyword()), SysLoginLog::getUsername, query.getKeyword())
                .eq(loginType != null, SysLoginLog::getLoginType,
                        loginType == null ? null : loginType.getCode())
                .eq(query.getStatus() != null, SysLoginLog::getStatus, query.getStatus())
                .ge(start != null, SysLoginLog::getLoginTime, start)
                .le(end != null, SysLoginLog::getLoginTime, end)
                .orderByDesc(SysLoginLog::getId));
        return PageResult.of(page);
    }
}
