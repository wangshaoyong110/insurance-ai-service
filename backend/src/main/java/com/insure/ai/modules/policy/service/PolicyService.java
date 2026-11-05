package com.insure.ai.modules.policy.service;

import com.insure.ai.common.PageResult;
import com.insure.ai.modules.policy.dto.ClaimDetailVO;
import com.insure.ai.modules.policy.dto.ClaimQuery;
import com.insure.ai.modules.policy.dto.PolicyQuery;
import com.insure.ai.modules.policy.entity.InsClaim;
import com.insure.ai.modules.policy.entity.InsClaimProgress;
import com.insure.ai.modules.policy.entity.InsPolicy;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
public interface PolicyService {

    /**
     * 保单分页
     */
    PageResult<InsPolicy> pagePolicies(PolicyQuery query);

    /**
     * 保单详情(含客户信息与理赔次数)
     */
    InsPolicy policyDetail(Long id);

    /**
     * 理赔分页
     */
    PageResult<InsClaim> pageClaims(ClaimQuery query);

    /**
     * 理赔详情(案件 + 保单 + 时间轴)
     */
    ClaimDetailVO claimDetail(Long id);

    /**
     * 当前客户名下全部保单(按生效日期倒序)
     */
    List<InsPolicy> listByCustomer(Long cusUserId);

    /**
     * 按保单号查询当前客户名下保单
     * <p>customer 条件不可省略:否则只凭保单号就能查到他人的保单</p>
     */
    InsPolicy findByCustomerAndPolicyNo(Long cusUserId, String policyNo);

    /**
     * 当前客户名下理赔记录
     *
     * @param policyNo 保单号,为空则查全部
     */
    List<InsClaim> listClaimsByCustomer(Long cusUserId, String policyNo);

    /**
     * 当前客户名下仍有效的保单(用于保障分析)
     * <p>有效 = 状态有效且在保障期内,已退保/满期/中止的保单不算已有保障</p>
     */
    List<InsPolicy> listEffectiveByCustomer(Long cusUserId);

    /**
     * 待缴保费:下次缴费日落在 [from, to] 区间内的有效保单
     */
    List<InsPolicy> listUpcomingPayments(Long cusUserId, LocalDate from, LocalDate to);

    /**
     * 逾期未缴:应缴日已过、保单仍有效的记录
     * <p>只查未来区间会把"已经欠缴"的保单漏掉,而这恰恰是保单中止、失效的直接原因,
     * 所以逾期单独查一次,由上层优先提示。</p>
     *
     * @param cusUserId 客户ID
     * @param today     今天
     */
    List<InsPolicy> listOverduePayments(Long cusUserId, LocalDate today);

    /**
     * 按理赔案号查询当前客户名下理赔
     */
    InsClaim findClaimByCustomer(Long cusUserId, String claimNo);

    /**
     * 理赔进度时间轴
     */
    List<InsClaimProgress> progressOf(Long claimId);
}
