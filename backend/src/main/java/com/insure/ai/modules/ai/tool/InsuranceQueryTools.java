package com.insure.ai.modules.ai.tool;

import com.insure.ai.common.enums.ClaimStatusEnum;
import com.insure.ai.common.enums.PolicyCategoryEnum;
import com.insure.ai.common.enums.PolicyStatusEnum;
import com.insure.ai.modules.policy.entity.InsClaim;
import com.insure.ai.modules.policy.entity.InsClaimProgress;
import com.insure.ai.modules.policy.entity.InsPolicy;
import com.insure.ai.modules.policy.service.PolicyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 保单/理赔查询工具(AI Function Calling)
 * <p>
 * <b>安全设计要点:</b>
 * <ul>
 *     <li>{@code cusUserId} 由服务端从登录态注入,<b>不作为工具参数暴露给模型</b>,
 *         模型无法通过编造参数越权访问他人保单</li>
 *     <li>每次对话都 new 一个新实例,避免请求间共享状态</li>
 *     <li>工具内部捕获所有异常并返回可读文案,避免异常打断整轮对话</li>
 * </ul>
 * <p>
 * 只暴露"当前登录客户"的数据,因此提示词中无需让客户提供保单号即可直接查询。
 *
 * @author insurance-ai
 */
@Slf4j
public class InsuranceQueryTools {

    /** 单次返回的最大理赔条数,避免把上下文撑爆 */
    private static final int MAX_CLAIMS = 10;

    private final PolicyService policyService;
    private final Long cusUserId;

    public InsuranceQueryTools(PolicyService policyService, Long cusUserId) {
        this.policyService = policyService;
        this.cusUserId = cusUserId;
    }

    /**
     * 查询当前客户名下的保单清单
     */
    @Tool(name = "listMyPolicies", description = """
            查询当前登录客户名下的全部保单清单(产品名称、保单号、险种、保额、年缴保费、生效与到期日、状态)。
            当客户询问"我买了什么保险""我有哪些保单""我的保额是多少""保单什么时候到期"时调用本工具。
            不需要客户提供任何身份信息或保单号,系统按登录身份自动过滤。
            """)
    public String listMyPolicies() {
        try {
            if (cusUserId == null) {
                return "未获取到当前登录客户,无法查询保单。";
            }
            List<InsPolicy> policies = policyService.listByCustomer(cusUserId);
            if (policies.isEmpty()) {
                return "当前客户名下没有查询到保单记录。";
            }
            String body = policies.stream()
                    .map(policy -> "%s(保单号 %s,保额 %s 元,年缴 %s 元,%s 至 %s,%s)"
                            .formatted(policy.getProductName(), policy.getPolicyNo(),
                                    money(policy.getCoverageAmount()), money(policy.getPremium()),
                                    date(policy.getEffectiveDate()), date(policy.getExpireDate()),
                                    PolicyStatusEnum.descOf(policy.getStatus())))
                    .collect(Collectors.joining("\n"));
            return "当前客户共 %d 张保单:\n%s".formatted(policies.size(), body);
        } catch (Exception e) {
            log.warn("[AI工具] 查询保单清单失败 cusUserId={}", cusUserId, e);
            return "保单查询服务暂时不可用,请稍后再试或联系人工客服。";
        }
    }

    /**
     * 保单详情
     */
    @Tool(name = "getPolicyDetail", description = """
            按保单号查询当前登录客户名下某张保单的详细信息(含险种、投保人、被保人、保额、保费、缴费年限、下次缴费日、当前状态、历史理赔次数)。
            当客户询问某一张保单的具体情况、下次缴费时间、还能不能理赔时调用本工具。
            保单号必须来自客户消息或 listMyPolicies 的返回结果;若客户未提供保单号,先调用 listMyPolicies。
            只能查到当前登录客户自己的保单,查不到他人保单。
            """)
    public String getPolicyDetail(
            @ToolParam(description = "保单号,例如 P202403010002") String policyNo) {
        try {
            if (cusUserId == null) {
                return "未获取到当前登录客户,无法查询保单。";
            }
            if (!StringUtils.hasText(policyNo)) {
                return "请先提供保单号,或先查询客户名下保单清单。";
            }
            InsPolicy policy = policyService.findByCustomerAndPolicyNo(cusUserId, policyNo);
            if (policy == null) {
                return "未查询到保单号为 %s 的保单。请确认保单号是否正确,该保单可能不属于当前登录客户。"
                        .formatted(policyNo);
            }
            List<InsClaim> claims = policyService.listClaimsByCustomer(cusUserId, policy.getPolicyNo());
            return """
                    保单详情:
                    产品名称:%s(%s)
                    保单号:%s
                    投保人:%s / 被保人:%s
                    保额:%s 元,年缴保费:%s 元
                    缴费年限:%s
                    保险期间:%s 至 %s
                    下次缴费日:%s
                    当前状态:%s
                    历史理赔:%d 次
                    """.formatted(policy.getProductName(),
                    PolicyCategoryEnum.descOf(policy.getCategory()),
                    policy.getPolicyNo(),
                    nullToDash(policy.getHolderName()), nullToDash(policy.getInsuredName()),
                    money(policy.getCoverageAmount()), money(policy.getPremium()),
                    policy.getPayYears() == null ? "趸交" : policy.getPayYears() + " 年",
                    date(policy.getEffectiveDate()), date(policy.getExpireDate()),
                    date(policy.getNextPayDate()),
                    PolicyStatusEnum.descOf(policy.getStatus()),
                    claims.size()).trim();
        } catch (Exception e) {
            log.warn("[AI工具] 查询保单详情失败 cusUserId={} policyNo={}", cusUserId, policyNo, e);
            return "保单查询服务暂时不可用,请稍后再试或联系人工客服。";
        }
    }

    /**
     * 理赔记录列表
     */
    @Tool(name = "listMyClaims", description = """
            查询当前登录客户名下的理赔记录(理赔案号、关联保单、理赔类型、报案日期、申请金额、核定金额、当前状态与处理环节)。
            当客户询问"我的理赔到哪一步了""理赔进度""赔了多少钱""有没有理赔记录"时调用本工具。
            不需要客户提供身份信息;若客户指定了某张保单,可传入保单号缩小范围。
            """)
    public String listMyClaims(
            @ToolParam(required = false, description = "保单号,可留空表示查询全部理赔记录") String policyNo) {
        try {
            if (cusUserId == null) {
                return "未获取到当前登录客户,无法查询理赔记录。";
            }
            List<InsClaim> claims = policyService.listClaimsByCustomer(cusUserId,
                    StringUtils.hasText(policyNo) ? policyNo : null);
            if (claims.isEmpty()) {
                return StringUtils.hasText(policyNo)
                        ? "该保单下没有理赔记录。"
                        : "当前客户名下没有查询到理赔记录。";
            }
            List<InsClaim> limited = claims.size() > MAX_CLAIMS ? claims.subList(0, MAX_CLAIMS) : claims;
            String body = limited.stream()
                    .map(claim -> "%s(%s,报案 %s,申请 %s 元,%s%s)"
                            .formatted(claim.getClaimNo(), claim.getPolicyNo(),
                                    date(claim.getReportDate()), money(claim.getClaimAmount()),
                                    ClaimStatusEnum.descOf(claim.getStatus()),
                                    claim.getApprovedAmount() == null ? ""
                                            : ",核定赔付 " + money(claim.getApprovedAmount()) + " 元"))
                    .collect(Collectors.joining("\n"));
            String suffix = claims.size() > MAX_CLAIMS
                    ? "\n(仅展示最近 %d 条,共 %d 条)".formatted(MAX_CLAIMS, claims.size()) : "";
            return "共 %d 条理赔记录:\n%s%s".formatted(claims.size(), body, suffix);
        } catch (Exception e) {
            log.warn("[AI工具] 查询理赔记录失败 cusUserId={} policyNo={}", cusUserId, policyNo, e);
            return "理赔查询服务暂时不可用,请稍后再试或联系人工客服。";
        }
    }

    /**
     * 理赔进度
     */
    @Tool(name = "getClaimProgress", description = """
            按理赔案号查询当前登录客户某笔理赔的详细进度(各环节、处理人、时间、是否完成),以及申请金额、核定金额、预计赔付日、拒赔原因。
            当客户询问某一笔理赔卡在哪里、还需要等多久、为什么被拒赔时调用本工具。
            理赔案号必须来自客户消息或 listMyClaims 的返回结果;若客户未提供,先调用 listMyClaims。
            """)
    public String getClaimProgress(
            @ToolParam(description = "理赔案号,例如 CLM20261020001") String claimNo) {
        try {
            if (cusUserId == null) {
                return "未获取到当前登录客户,无法查询理赔进度。";
            }
            if (!StringUtils.hasText(claimNo)) {
                return "请先提供理赔案号,或先查询客户的理赔记录列表。";
            }
            InsClaim claim = policyService.findClaimByCustomer(cusUserId, claimNo);
            if (claim == null) {
                return "未查询到理赔案号为 %s 的记录。请确认案号是否正确,该理赔可能不属于当前登录客户。"
                        .formatted(claimNo);
            }
            List<InsClaimProgress> progress = policyService.progressOf(claim.getId());
            String timeline = progress.isEmpty() ? "暂无进度记录"
                    : progress.stream()
                    .map(item -> "%s %s %s%s".formatted(
                            date(item.getProgressTime() == null ? null : item.getProgressTime().toLocalDate()),
                            item.getNode(),
                            item.getNodeDesc() == null ? "" : item.getNodeDesc(),
                            Integer.valueOf(0).equals(item.getFinished()) ? "(进行中)" : ""))
                    .collect(Collectors.joining("\n"));

            StringBuilder text = new StringBuilder();
            text.append("理赔案号:").append(claim.getClaimNo())
                    .append("\n关联保单:").append(nullToDash(claim.getPolicyNo()))
                    .append("\n报案日期:").append(date(claim.getReportDate()))
                    .append("\n申请金额:").append(money(claim.getClaimAmount())).append(" 元")
                    .append("\n当前状态:").append(ClaimStatusEnum.descOf(claim.getStatus()))
                    .append("\n当前环节:").append(nullToDash(claim.getCurrentNode()))
                    .append("\n处理人:").append(nullToDash(claim.getHandler()));
            if (claim.getApprovedAmount() != null) {
                text.append("\n核定赔付:").append(money(claim.getApprovedAmount())).append(" 元");
            }
            if (claim.getExpectedPayDate() != null && !ClaimStatusEnum.isClosed(claim.getStatus())) {
                text.append("\n预计赔付日:").append(date(claim.getExpectedPayDate()));
            }
            if (StringUtils.hasText(claim.getRejectReason())) {
                text.append("\n拒赔原因:").append(claim.getRejectReason());
            }
            text.append("\n进度明细:\n").append(timeline);
            return text.toString();
        } catch (Exception e) {
            log.warn("[AI工具] 查询理赔进度失败 cusUserId={} claimNo={}", cusUserId, claimNo, e);
            return "理赔查询服务暂时不可用,请稍后再试或联系人工客服。";
        }
    }

    // ---------------- 格式化工具 ----------------

    private String money(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String date(LocalDate value) {
        return value == null ? "—" : value.toString();
    }

    private String nullToDash(String value) {
        return StringUtils.hasText(value) ? value : "—";
    }
}
