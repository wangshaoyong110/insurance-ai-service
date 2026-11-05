package com.insure.ai.modules.system.service;

import com.insure.ai.common.PageResult;
import com.insure.ai.common.enums.UserTypeEnum;
import com.insure.ai.modules.system.dto.LogQuery;
import com.insure.ai.modules.system.entity.SysLoginLog;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 登录日志服务:写入 + 分页查询
 *
 * @author insurance-ai
 */
public interface SysLoginLogService {

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
    void record(Long userId, String username, UserTypeEnum loginType, HttpServletRequest request, boolean success, String msg);

    /**
     * 分页查询登录日志
     */
    PageResult<SysLoginLog> page(LogQuery query, UserTypeEnum loginType);
}
