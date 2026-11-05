package com.insure.ai.modules.policy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.insure.ai.modules.policy.entity.InsPolicy;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 保单 Mapper
 *
 * @author insurance-ai
 */
@Mapper
public interface InsPolicyMapper extends BaseMapper<InsPolicy> {

    /**
     * 统计每个客户的保单数量
     *
     * @return key: cusUserId, value: policyCount
     */
    List<Map<String, Object>> countGroupByCusUser();
}
