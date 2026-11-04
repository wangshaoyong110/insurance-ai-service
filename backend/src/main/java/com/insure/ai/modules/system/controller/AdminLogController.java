package com.insure.ai.modules.system.controller;

import com.insure.ai.common.PageResult;
import com.insure.ai.common.Result;
import com.insure.ai.common.enums.UserTypeEnum;
import com.insure.ai.modules.system.dto.LogQuery;
import com.insure.ai.modules.system.entity.SysLoginLog;
import com.insure.ai.modules.system.entity.SysOperLog;
import com.insure.ai.modules.system.service.SysLoginLogService;
import com.insure.ai.modules.system.service.SysOperLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台日志查询控制器(登录日志 / 操作日志)
 *
 * @author insurance-ai
 */
@Tag(name = "管理端-日志中心", description = "登录日志与操作日志查询")
@RestController
@RequestMapping("/admin/log")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminLogController {

    private final SysLoginLogService sysLoginLogService;
    private final SysOperLogService sysOperLogService;

    /**
     * 登录日志分页
     *
     * @param loginType 登录端:ADMIN-管理端 CUSTOMER-客户端,为空表示全部
     */
    @GetMapping("/login")
    public Result<PageResult<SysLoginLog>> loginLog(LogQuery query,
                                                    @RequestParam(required = false) String loginType) {
        UserTypeEnum type = loginType == null || loginType.isBlank() ? null : UserTypeEnum.of(loginType);
        return Result.success(sysLoginLogService.page(query, type));
    }

    /**
     * 操作日志分页
     */
    @GetMapping("/oper")
    public Result<PageResult<SysOperLog>> operLog(LogQuery query) {
        return Result.success(sysOperLogService.page(query));
    }
}
