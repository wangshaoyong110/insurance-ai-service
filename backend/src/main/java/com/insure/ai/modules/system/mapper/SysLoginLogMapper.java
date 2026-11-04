package com.insure.ai.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.insure.ai.modules.system.entity.SysLoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志 Mapper
 *
 * @author insurance-ai
 */
@Mapper
public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {
}
