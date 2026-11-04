package com.insure.ai.modules.policy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.insure.ai.modules.policy.entity.InsClaimProgress;
import org.apache.ibatis.annotations.Mapper;

/**
 * 理赔进度 Mapper
 *
 * @author insurance-ai
 */
@Mapper
public interface InsClaimProgressMapper extends BaseMapper<InsClaimProgress> {
}
