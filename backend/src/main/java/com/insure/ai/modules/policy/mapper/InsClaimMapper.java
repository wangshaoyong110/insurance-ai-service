package com.insure.ai.modules.policy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.insure.ai.modules.policy.entity.InsClaim;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 理赔 Mapper
 *
 * @author insurance-ai
 */
@Mapper
public interface InsClaimMapper extends BaseMapper<InsClaim> {

    /**
     * 统计每张保单的理赔次数
     *
     * @return key: policyId, value: claimCount
     */
    @Select("""
            SELECT policy_id AS policyId, COUNT(*) AS claimCount
            FROM ins_claim
            WHERE deleted = 0
            GROUP BY policy_id
            """)
    List<Map<String, Object>> countGroupByPolicy();
}
