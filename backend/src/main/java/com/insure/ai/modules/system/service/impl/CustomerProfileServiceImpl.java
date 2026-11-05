package com.insure.ai.modules.system.service.impl;

import com.insure.ai.modules.system.service.CusUserService;
import com.insure.ai.modules.system.service.CustomerProfileService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.insure.ai.modules.chat.entity.ChatSession;
import com.insure.ai.modules.chat.mapper.ChatSessionMapper;
import com.insure.ai.modules.policy.entity.InsClaim;
import com.insure.ai.modules.policy.entity.InsPolicy;
import com.insure.ai.modules.policy.service.PolicyService;
import com.insure.ai.modules.system.dto.CusUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 客户 360 视图
 * <p>
 * 坐席处理工单时最缺的是上下文：这个客户买过什么、有没有在理赔、之前聊过什么。
 * 原先后台客户列表只有账号信息，点进去也看不到业务数据，只能再切到保单页按姓名搜一遍。
 * 这里把四块数据一次性聚合出来。
 * </p>
 *
 * @author insurance-ai
 */
@Service
@RequiredArgsConstructor
public class CustomerProfileServiceImpl implements CustomerProfileService {

    /** 最近会话展示条数 */
    private static final int RECENT_SESSION_LIMIT = 10;

    private final CusUserService cusUserService;
    private final PolicyService policyService;
    private final ChatSessionMapper chatSessionMapper;

    /**
     * 客户全景信息
     */
    @Override
    public Map<String, Object> profile(Long cusUserId) {
        CusUserVO customer = cusUserService.detail(cusUserId);
        List<InsPolicy> policies = policyService.listByCustomer(cusUserId);
        List<InsClaim> claims = policyService.listClaimsByCustomer(cusUserId, null);
        List<ChatSession> sessions = chatSessionMapper.selectList(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getCusUserId, cusUserId)
                .orderByDesc(ChatSession::getLastMessageTime)
                .last("LIMIT " + RECENT_SESSION_LIMIT));

        // 有效保单的保额合计：坐席判断客户保障水平时最直观的一个数字
        BigDecimal activeCoverage = policies.stream()
                .filter(item -> Objects.equals(item.getStatus(), 1))
                .map(InsPolicy::getCoverageAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long activePolicies = policies.stream()
                .filter(item -> Objects.equals(item.getStatus(), 1))
                .count();
        long openClaims = claims.stream()
                .filter(item -> item.getStatus() != null && item.getStatus() >= 1 && item.getStatus() <= 3)
                .count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("policyCount", policies.size());
        summary.put("activePolicyCount", activePolicies);
        summary.put("activeCoverage", activeCoverage);
        summary.put("claimCount", claims.size());
        summary.put("openClaimCount", openClaims);
        summary.put("sessionCount", customer.getSessionCount());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("customer", customer);
        data.put("summary", summary);
        data.put("policies", policies);
        data.put("claims", claims);
        data.put("sessions", sessions);
        return data;
    }
}
