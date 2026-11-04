package com.insure.ai.modules.policy.controller;

import com.insure.ai.common.PageResult;
import com.insure.ai.common.Result;
import com.insure.ai.modules.policy.dto.ClaimDetailVO;
import com.insure.ai.modules.policy.dto.ClaimQuery;
import com.insure.ai.modules.policy.dto.PolicyQuery;
import com.insure.ai.modules.policy.entity.InsClaim;
import com.insure.ai.modules.policy.entity.InsPolicy;
import com.insure.ai.modules.policy.service.PolicyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台保单 / 理赔查询控制器
 * <p>只读查询:数据由核心业务系统同步,后台不在此处变更保单状态</p>
 *
 * @author insurance-ai
 */
@Tag(name = "管理端-保单理赔", description = "管理端查询客户保单、理赔案件与理赔进度")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPolicyController {

    private final PolicyService policyService;

    /**
     * 保单分页
     */
    @GetMapping("/policy/page")
    public Result<PageResult<InsPolicy>> pagePolicies(PolicyQuery query) {
        return Result.success(policyService.pagePolicies(query));
    }

    /**
     * 保单详情
     */
    @GetMapping("/policy/{id}")
    public Result<InsPolicy> policyDetail(@PathVariable Long id) {
        return Result.success(policyService.policyDetail(id));
    }

    /**
     * 理赔分页
     */
    @GetMapping("/claim/page")
    public Result<PageResult<InsClaim>> pageClaims(ClaimQuery query) {
        return Result.success(policyService.pageClaims(query));
    }

    /**
     * 理赔详情(含进度时间轴)
     */
    @GetMapping("/claim/{id}")
    public Result<ClaimDetailVO> claimDetail(@PathVariable Long id) {
        return Result.success(policyService.claimDetail(id));
    }
}
