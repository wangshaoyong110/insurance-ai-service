package com.insure.ai.modules.policy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.insure.ai.modules.policy.entity.InsClaim;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
     * 统计指定保单的理赔次数
     * <p>
     * 原来是无条件 {@code GROUP BY policy_id}，每翻一页保单都把整张 ins_claim 聚合一遍，
     * 与当前页毫无关系。改成按当前页的 policy_id 过滤后，扫描量只与页大小相关。
     * </p>
     *
     * @param policyIds 当前页保单ID
     * @return key: policyId, value: claimCount
     */
    List<Map<String, Object>> countByPolicyIds(@Param("policyIds") List<Long> policyIds);
}
