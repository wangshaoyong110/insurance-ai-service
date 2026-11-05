package com.insure.ai.modules.policy.service.impl;

import com.insure.ai.modules.policy.service.PolicyService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.PageResult;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.PolicyStatusEnum;
import com.insure.ai.modules.policy.dto.ClaimDetailVO;
import com.insure.ai.modules.policy.dto.ClaimQuery;
import com.insure.ai.modules.policy.dto.PolicyQuery;
import com.insure.ai.modules.policy.entity.InsClaim;
import com.insure.ai.modules.policy.entity.InsClaimProgress;
import com.insure.ai.modules.policy.entity.InsPolicy;
import com.insure.ai.modules.policy.mapper.InsClaimMapper;
import com.insure.ai.modules.policy.mapper.InsClaimProgressMapper;
import com.insure.ai.modules.policy.mapper.InsPolicyMapper;
import com.insure.ai.modules.system.entity.CusUser;
import com.insure.ai.modules.system.mapper.CusUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 保单与理赔服务
 * <p>
 * 分两类入口:
 * <ul>
 *     <li><b>管理端</b>:跨客户查询,带分页与筛选,用于后台页面</li>
 *     <li><b>客户侧</b>:所有方法第一个参数都是 {@code cusUserId},
 *         由 AI 工具从登录态注入,SQL 条件里强制带上该字段,杜绝越权查询</li>
 * </ul>
 *
 * @author insurance-ai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyServiceImpl implements PolicyService {

    private final InsPolicyMapper policyMapper;
    private final InsClaimMapper claimMapper;
    private final InsClaimProgressMapper claimProgressMapper;
    private final CusUserMapper cusUserMapper;

    // ==================================================================
    // 管理端
    // ==================================================================

    /**
     * 保单分页
     */
    @Override
    public PageResult<InsPolicy> pagePolicies(PolicyQuery query) {
        // 客户关键字先解析成 ID 列表,避免拼接 SQL
        List<Long> customerIds = null;
        if (StringUtils.hasText(query.getCustomer())) {
            customerIds = matchCustomerIds(query.getCustomer());
            if (customerIds.isEmpty()) {
                return PageResult.empty(query.getPageNum(), query.getPageSize());
            }
        }
        Page<InsPolicy> page = new Page<>(query.getPageNum(), query.getPageSize());
        policyMapper.selectPage(page, Wrappers.<InsPolicy>lambdaQuery()
                .like(StringUtils.hasText(query.getPolicyNo()), InsPolicy::getPolicyNo, query.getPolicyNo())
                .eq(StringUtils.hasText(query.getCategory()), InsPolicy::getCategory, query.getCategory())
                .eq(query.getStatus() != null, InsPolicy::getStatus, query.getStatus())
                .in(customerIds != null, InsPolicy::getCusUserId, customerIds)
                .orderByDesc(InsPolicy::getId));
        PageResult<InsPolicy> result = PageResult.of(page, item -> item);
        fillPolicyExtra(result.getRecords());
        return result;
    }

    /**
     * 保单详情(含客户信息与理赔次数)
     */
    @Override
    public InsPolicy policyDetail(Long id) {
        InsPolicy policy = policyMapper.selectById(id);
        if (policy == null) {
            throw new BusinessException(ResultCode.POLICY_NOT_EXIST);
        }
        fillPolicyExtra(List.of(policy));
        return policy;
    }

    /**
     * 理赔分页
     */
    @Override
    public PageResult<InsClaim> pageClaims(ClaimQuery query) {
        List<Long> customerIds = null;
        if (StringUtils.hasText(query.getCustomer())) {
            customerIds = matchCustomerIds(query.getCustomer());
            if (customerIds.isEmpty()) {
                return PageResult.empty(query.getPageNum(), query.getPageSize());
            }
        }
        Page<InsClaim> page = new Page<>(query.getPageNum(), query.getPageSize());
        claimMapper.selectPage(page, Wrappers.<InsClaim>lambdaQuery()
                .like(StringUtils.hasText(query.getClaimNo()), InsClaim::getClaimNo, query.getClaimNo())
                .like(StringUtils.hasText(query.getPolicyNo()), InsClaim::getPolicyNo, query.getPolicyNo())
                .eq(StringUtils.hasText(query.getClaimType()), InsClaim::getClaimType, query.getClaimType())
                .eq(query.getStatus() != null, InsClaim::getStatus, query.getStatus())
                .in(customerIds != null, InsClaim::getCusUserId, customerIds)
                .orderByDesc(InsClaim::getId));
        PageResult<InsClaim> result = PageResult.of(page, item -> item);
        fillClaimExtra(result.getRecords());
        return result;
    }

    /**
     * 理赔详情(案件 + 保单 + 时间轴)
     */
    @Override
    public ClaimDetailVO claimDetail(Long id) {
        InsClaim claim = claimMapper.selectById(id);
        if (claim == null) {
            throw new BusinessException(ResultCode.CLAIM_NOT_EXIST);
        }
        fillClaimExtra(List.of(claim));
        ClaimDetailVO vo = new ClaimDetailVO();
        vo.setClaim(claim);
        vo.setPolicy(claim.getPolicyId() == null ? null : policyMapper.selectById(claim.getPolicyId()));
        vo.setProgress(progressOf(claim.getId()));
        return vo;
    }

    // ==================================================================
    // 客户侧(供 AI 工具调用,必须以登录态 cusUserId 过滤)
    // ==================================================================

    /**
     * 当前客户名下全部保单(按生效日期倒序)
     */
    @Override
    public List<InsPolicy> listByCustomer(Long cusUserId) {
        return policyMapper.selectList(Wrappers.<InsPolicy>lambdaQuery()
                .eq(InsPolicy::getCusUserId, cusUserId)
                .orderByDesc(InsPolicy::getEffectiveDate)
                .orderByDesc(InsPolicy::getId));
    }

    /**
     * 按保单号查询当前客户名下保单
     * <p>customer 条件不可省略:否则只凭保单号就能查到他人的保单</p>
     */
    @Override
    public InsPolicy findByCustomerAndPolicyNo(Long cusUserId, String policyNo) {
        if (!StringUtils.hasText(policyNo)) {
            return null;
        }
        return policyMapper.selectOne(Wrappers.<InsPolicy>lambdaQuery()
                .eq(InsPolicy::getCusUserId, cusUserId)
                .eq(InsPolicy::getPolicyNo, policyNo.trim())
                .last("LIMIT 1"));
    }

    /**
     * 当前客户名下理赔记录
     *
     * @param policyNo 保单号,为空则查全部
     */
    @Override
    public List<InsClaim> listClaimsByCustomer(Long cusUserId, String policyNo) {
        return claimMapper.selectList(Wrappers.<InsClaim>lambdaQuery()
                .eq(InsClaim::getCusUserId, cusUserId)
                .eq(StringUtils.hasText(policyNo), InsClaim::getPolicyNo,
                        policyNo == null ? null : policyNo.trim())
                .orderByDesc(InsClaim::getReportDate)
                .orderByDesc(InsClaim::getId));
    }

    /**
     * 当前客户名下仍有效的保单(用于保障分析)
     * <p>有效 = 状态有效且在保障期内,已退保/满期/中止的保单不算已有保障</p>
     */
    @Override
    public List<InsPolicy> listEffectiveByCustomer(Long cusUserId) {
        return policyMapper.selectList(Wrappers.<InsPolicy>lambdaQuery()
                .eq(InsPolicy::getCusUserId, cusUserId)
                .eq(InsPolicy::getStatus, PolicyStatusEnum.EFFECTIVE.getCode())
                .ge(InsPolicy::getExpireDate, LocalDate.now())
                .orderByAsc(InsPolicy::getExpireDate));
    }

    /**
     * 待缴保费:下次缴费日落在 [from, to] 区间内的有效保单
     */
    @Override
    public List<InsPolicy> listUpcomingPayments(Long cusUserId, LocalDate from, LocalDate to) {
        return policyMapper.selectList(Wrappers.<InsPolicy>lambdaQuery()
                .eq(InsPolicy::getCusUserId, cusUserId)
                .eq(InsPolicy::getStatus, PolicyStatusEnum.EFFECTIVE.getCode())
                .isNotNull(InsPolicy::getNextPayDate)
                .ge(InsPolicy::getNextPayDate, from)
                .le(InsPolicy::getNextPayDate, to)
                .orderByAsc(InsPolicy::getNextPayDate));
    }

    /**
     * 逾期未缴:应缴日已过、保单仍有效的记录
     * <p>只查未来区间会把"已经欠缴"的保单漏掉,而这恰恰是保单中止、失效的直接原因,
     * 所以逾期单独查一次,由上层优先提示。</p>
     *
     * @param cusUserId 客户ID
     * @param today     今天
     */
    @Override
    public List<InsPolicy> listOverduePayments(Long cusUserId, LocalDate today) {
        return policyMapper.selectList(Wrappers.<InsPolicy>lambdaQuery()
                .eq(InsPolicy::getCusUserId, cusUserId)
                .eq(InsPolicy::getStatus, PolicyStatusEnum.EFFECTIVE.getCode())
                .isNotNull(InsPolicy::getNextPayDate)
                .lt(InsPolicy::getNextPayDate, today)
                .orderByAsc(InsPolicy::getNextPayDate));
    }

    /**
     * 按理赔案号查询当前客户名下理赔
     */
    @Override
    public InsClaim findClaimByCustomer(Long cusUserId, String claimNo) {
        if (!StringUtils.hasText(claimNo)) {
            return null;
        }
        return claimMapper.selectOne(Wrappers.<InsClaim>lambdaQuery()
                .eq(InsClaim::getCusUserId, cusUserId)
                .eq(InsClaim::getClaimNo, claimNo.trim())
                .last("LIMIT 1"));
    }

    /**
     * 理赔进度时间轴
     */
    @Override
    public List<InsClaimProgress> progressOf(Long claimId) {
        return claimProgressMapper.selectList(Wrappers.<InsClaimProgress>lambdaQuery()
                .eq(InsClaimProgress::getClaimId, claimId)
                .orderByAsc(InsClaimProgress::getProgressTime)
                .orderByAsc(InsClaimProgress::getId));
    }

    // ==================================================================
    // 内部方法
    // ==================================================================

    /**
     * 按姓名 / 手机号匹配客户ID
     */
    private List<Long> matchCustomerIds(String keyword) {
        return cusUserMapper.selectList(Wrappers.<CusUser>lambdaQuery()
                        .and(wrapper -> wrapper
                                .like(CusUser::getNickname, keyword)
                                .or().like(CusUser::getRealName, keyword)
                                .or().like(CusUser::getPhone, keyword)))
                .stream()
                .map(CusUser::getId)
                .toList();
    }

    /**
     * 回填保单的客户信息与理赔次数
     */
    private void fillPolicyExtra(List<InsPolicy> policies) {
        if (policies.isEmpty()) {
            return;
        }
        Map<Long, CusUser> userMap = loadUsers(policies.stream()
                .map(InsPolicy::getCusUserId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, Long> countMap = new HashMap<>();
        List<Long> policyIds = policies.stream().map(InsPolicy::getId).filter(Objects::nonNull).toList();
        if (!policyIds.isEmpty()) {
            claimMapper.countByPolicyIds(policyIds).forEach(row ->
                    countMap.put(toLong(row.get("policyId")), toLong(row.get("claimCount"))));
        }
        for (InsPolicy item : policies) {
            CusUser user = userMap.get(item.getCusUserId());
            if (user != null) {
                item.setCustomerName(displayName(user));
                item.setCustomerPhone(user.getPhone());
            }
            item.setClaimCount(countMap.getOrDefault(item.getId(), 0L));
        }
    }

    /**
     * 回填理赔记录的客户信息与产品名称
     */
    private void fillClaimExtra(List<InsClaim> claims) {
        if (claims.isEmpty()) {
            return;
        }
        Map<Long, CusUser> userMap = loadUsers(claims.stream()
                .map(InsClaim::getCusUserId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<Long> policyIds = claims.stream()
                .map(InsClaim::getPolicyId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> productMap = policyIds.isEmpty() ? Map.of()
                : policyMapper.selectByIds(policyIds).stream()
                .collect(Collectors.toMap(InsPolicy::getId, InsPolicy::getProductName, (a, b) -> a));
        for (InsClaim item : claims) {
            CusUser user = userMap.get(item.getCusUserId());
            if (user != null) {
                item.setCustomerName(displayName(user));
                item.setCustomerPhone(user.getPhone());
            }
            item.setProductName(productMap.get(item.getPolicyId()));
        }
    }

    private Map<Long, CusUser> loadUsers(java.util.Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return cusUserMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(CusUser::getId, user -> user, (a, b) -> a));
    }

    /**
     * 客户展示名:优先真实姓名,其次昵称
     */
    private String displayName(CusUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getNickname();
    }

    private Long toLong(Object value) {
        return value == null ? 0L : Long.parseLong(value.toString());
    }
}
