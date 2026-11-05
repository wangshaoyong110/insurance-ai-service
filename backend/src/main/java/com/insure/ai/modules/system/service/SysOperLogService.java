package com.insure.ai.modules.system.service;

import com.insure.ai.common.PageResult;
import com.insure.ai.modules.system.dto.LogQuery;
import com.insure.ai.modules.system.entity.SysOperLog;

/**
 * 操作日志查询服务
 *
 * @author insurance-ai
 */
public interface SysOperLogService {

    /**
     * 分页查询操作日志
     */
    PageResult<SysOperLog> page(LogQuery query);
}
