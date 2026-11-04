package com.insure.ai.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.insure.ai.modules.system.entity.CusUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户端用户 Mapper
 *
 * @author insurance-ai
 */
@Mapper
public interface CusUserMapper extends BaseMapper<CusUser> {
}
