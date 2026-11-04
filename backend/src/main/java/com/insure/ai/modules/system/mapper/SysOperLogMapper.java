package com.insure.ai.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.insure.ai.modules.system.entity.SysOperLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper
 *
 * @author insurance-ai
 */
@Mapper
public interface SysOperLogMapper extends BaseMapper<SysOperLog> {
}
