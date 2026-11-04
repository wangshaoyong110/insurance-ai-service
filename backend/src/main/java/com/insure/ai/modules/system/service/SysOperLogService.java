package com.insure.ai.modules.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.insure.ai.common.PageResult;
import com.insure.ai.common.util.DateUtils;
import com.insure.ai.modules.system.dto.LogQuery;
import com.insure.ai.modules.system.entity.SysOperLog;
import com.insure.ai.modules.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 操作日志查询服务
 *
 * @author insurance-ai
 */
@Service
@RequiredArgsConstructor
public class SysOperLogService {

    private final SysOperLogMapper sysOperLogMapper;

    /**
     * 分页查询操作日志
     */
    public PageResult<SysOperLog> page(LogQuery query) {
        LocalDateTime start = DateUtils.parseStartOfDay(query.getStartDate());
        LocalDateTime end = DateUtils.parseEndOfDay(query.getEndDate());
        Page<SysOperLog> page = new Page<>(query.getPageNum(), query.getPageSize());
        sysOperLogMapper.selectPage(page, Wrappers.<SysOperLog>lambdaQuery()
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                        .like(SysOperLog::getOperName, query.getKeyword())
                        .or().like(SysOperLog::getModule, query.getKeyword())
                        .or().like(SysOperLog::getOperation, query.getKeyword()))
                .eq(query.getStatus() != null, SysOperLog::getStatus, query.getStatus())
                .ge(start != null, SysOperLog::getOperTime, start)
                .le(end != null, SysOperLog::getOperTime, end)
                .orderByDesc(SysOperLog::getId));
        return PageResult.of(page);
    }
}
