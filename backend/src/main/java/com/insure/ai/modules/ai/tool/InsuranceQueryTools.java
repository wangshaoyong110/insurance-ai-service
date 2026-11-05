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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    /** 待缴保费默认查询天数 */
    private static final int DEFAULT_PAYMENT_DAYS = 30;
    /** 寿险保单法定宽限期(天):宽限期内出险仍赔,超过则保单中止 */
    private static final int GRACE_PERIOD_DAYS = 60;
    /** 重疾保额参考下限(元):低于该值时在分析结果中给出保额提示 */
    private static final BigDecimal CRITICAL_REFERENCE_AMOUNT = new BigDecimal("300000");

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

    /**
     * 保障缺口分析
     * <p>
     * 客户问"我还缺什么保险"时，如果只让模型凭常识回答，很容易变成推销话术。
     * 这里先按保单事实算出已覆盖的险种，再由模型组织语言，结论有据可依。
     * </p>
     */
    @Tool(name = "analyzeCoverageGap", description = """
            分析当前登录客户已有保障覆盖情况与可能存在的缺口(按险种维度:重疾、医疗、意外、寿险、年金)。
            当客户询问"我还缺什么保险""我的保障够不够""要不要补充保险""帮我看看保障结构"时调用本工具。
            返回内容包含:在保保单清单、缺失的险种、重疾保额是否偏低。
            结论仅为基于已有保单的初步分析,不构成投保建议。
            """)
    public String analyzeCoverageGap() {
        try {
            if (cusUserId == null) {
                return "未获取到当前登录客户,无法分析保障情况。";
            }
            List<InsPolicy> policies = policyService.listEffectiveByCustomer(cusUserId);
            if (policies.isEmpty()) {
                return """
                        当前客户名下没有在保的有效保单(已退保、满期或中止的保单不计入保障)。
                        可以从重疾、医疗、意外三类基础保障开始了解,具体配置需结合家庭责任与预算,建议咨询持牌顾问。
                        """.trim();
            }

            Set<String> owned = policies.stream()
                    .map(item -> PolicyCategoryEnum.of(item.getCategory()).getCode())
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            StringBuilder text = new StringBuilder();
            text.append("当前在保有效保单共 ").append(policies.size()).append(" 张:\n");
            for (InsPolicy item : policies) {
                text.append("- ")
                        .append(PolicyCategoryEnum.descOf(item.getCategory())).append(' ')
                        .append(nullToDash(item.getProductName()))
                        .append(",保额 ").append(money(item.getCoverageAmount())).append(" 元")
                        .append(",保障至 ").append(date(item.getExpireDate()))
                        .append('\n');
            }

            List<String> gaps = new ArrayList<>();
            if (!owned.contains(PolicyCategoryEnum.MEDICAL.getCode())) {
                gaps.add("医疗险:住院、门诊费用没有报销渠道。重疾险是一次性给付,不能替代医疗费用报销");
            }
            if (!owned.contains(PolicyCategoryEnum.ACCIDENT.getCode())) {
                gaps.add("意外险:意外身故/伤残与意外医疗责任缺失,这类产品保费低、杠杆高");
            }
            if (!owned.contains(PolicyCategoryEnum.CRITICAL.getCode())) {
                gaps.add("重疾险:缺少确诊即给付的大额现金支持,收入中断期间压力较大");
            }
            if (!owned.contains(PolicyCategoryEnum.LIFE.getCode())) {
                gaps.add("寿险:若有房贷、子女教育等家庭责任,身故保障缺口需一并考虑");
            }

            if (gaps.isEmpty()) {
                text.append("\n四类基础保障(重疾、医疗、意外、寿险)均有覆盖,结构较完整。\n");
            } else {
                text.append("\n可能存在的缺口:\n");
                for (int i = 0; i < gaps.size(); i++) {
                    text.append(i + 1).append(". ").append(gaps.get(i)).append('\n');
                }
            }

            // 重疾保额单独提示:险种有了但保额过低,同样起不到作用
            BigDecimal criticalAmount = policies.stream()
                    .filter(item -> PolicyCategoryEnum.CRITICAL.getCode()
                            .equals(PolicyCategoryEnum.of(item.getCategory()).getCode()))
                    .map(InsPolicy::getCoverageAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (criticalAmount.compareTo(BigDecimal.ZERO) > 0
                    && criticalAmount.compareTo(CRITICAL_REFERENCE_AMOUNT) < 0) {
                text.append("\n保额提示:重疾险合计保额 ")
                        .append(money(criticalAmount)).append(" 元,低于常见参考区间(30 万至 50 万)。\n");
            }

            text.append("\n以上为基于已有保单的初步分析,不构成投保建议;具体产品责任、等待期与健康告知请以条款为准。");
            return text.toString().trim();
        } catch (Exception e) {
            log.warn("[AI工具] 保障缺口分析失败 cusUserId={}", cusUserId, e);
            return "保障分析服务暂时不可用,请稍后再试或联系人工客服。";
        }
    }

    /**
     * 待缴保费提醒
     * <p>库里有 next_pay_date 字段，但此前没有任何出口——客户不问，系统就不会提醒，
     * 而这恰恰是最容易造成保单失效的风险点。</p>
     */
    @Tool(name = "listUpcomingPayments", description = """
            查询当前登录客户的缴费情况:既包含已经逾期未缴的保单,也包含未来一段时间内需要缴费的保单。
            当客户询问"我什么时候要交保费""最近有没有要交的钱""我是不是忘了交保费""保费忘了交怎么办""宽限期是多久"时调用本工具。
            默认查询未来 30 天,可传入天数扩大范围(最多 180 天)。
            返回中若出现"已逾期",必须优先告知客户逾期天数与宽限期剩余天数。
            """)
    public String listUpcomingPayments(
            @ToolParam(required = false, description = "查询未来多少天,默认 30,最大 180") Integer days) {
        try {
            if (cusUserId == null) {
                return "未获取到当前登录客户,无法查询缴费计划。";
            }
            int range = days == null ? DEFAULT_PAYMENT_DAYS : Math.min(Math.max(days, 1), 180);
            LocalDate today = LocalDate.now();
            List<InsPolicy> overdue = policyService.listOverduePayments(cusUserId, today);
            List<InsPolicy> policies = policyService.listUpcomingPayments(
                    cusUserId, today, today.plusDays(range));
            if (overdue.isEmpty() && policies.isEmpty()) {
                return "未来 %d 天内没有待缴保费,也没有逾期未缴记录。".formatted(range);
            }

            StringBuilder text = new StringBuilder();
            // 逾期优先:这是保单中止/失效的直接原因,不能被"未来无缴费"的结论掩盖
            if (!overdue.isEmpty()) {
                BigDecimal overdueTotal = overdue.stream()
                        .map(InsPolicy::getPremium)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                text.append("【已逾期】有 ").append(overdue.size()).append(" 张保单已过应缴日,合计 ")
                        .append(money(overdueTotal)).append(" 元:\n");
                for (InsPolicy item : overdue) {
                    long passed = java.time.temporal.ChronoUnit.DAYS.between(item.getNextPayDate(), today);
                    long graceLeft = GRACE_PERIOD_DAYS - passed;
                    text.append("- ")
                            .append(nullToDash(item.getProductName()))
                            .append("(保单号 ").append(item.getPolicyNo()).append(')')
                            .append(",应缴 ").append(money(item.getPremium())).append(" 元")
                            .append(",应缴日 ").append(date(item.getNextPayDate()))
                            .append(",已逾期 ").append(passed).append(" 天");
                    if (graceLeft > 0) {
                        text.append(",宽限期还剩 ").append(graceLeft).append(" 天,尽快补缴不影响保障");
                    } else {
                        text.append(",已超过 ").append(GRACE_PERIOD_DAYS).append(" 天宽限期,保单可能已中止,需办理复效");
                    }
                    text.append('\n');
                }
                text.append('\n');
            }

            if (!policies.isEmpty()) {
                BigDecimal total = policies.stream()
                        .map(InsPolicy::getPremium)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                text.append("【未来 ").append(range).append(" 天】有 ").append(policies.size())
                        .append(" 张保单需要缴费,合计 ").append(money(total)).append(" 元:\n");
                for (InsPolicy item : policies) {
                    long remain = java.time.temporal.ChronoUnit.DAYS.between(today, item.getNextPayDate());
                    text.append("- ")
                            .append(nullToDash(item.getProductName()))
                            .append("(保单号 ").append(item.getPolicyNo()).append(')')
                            .append(",应缴 ").append(money(item.getPremium())).append(" 元")
                            .append(",缴费日 ").append(date(item.getNextPayDate()))
                            .append(remain == 0 ? "(今天到期)" : "(还有 " + remain + " 天)")
                            .append('\n');
                }
            } else {
                // 有逾期时不能再写"没有待缴保费",否则前后矛盾,客户会以为不用管
                text.append(overdue.isEmpty()
                        ? "【未来 " + range + " 天】没有待缴保费。\n"
                        : "【未来 " + range + " 天】没有新增待缴保费,请先处理上述逾期记录。\n");
            }

            text.append("\n提示:保单通常有 ").append(GRACE_PERIOD_DAYS)
                    .append(" 天宽限期,宽限期内出险仍可赔付;超过宽限期未缴,保单会中止,复效需重新健康告知。");
            return text.toString().trim();
        } catch (Exception e) {
            log.warn("[AI工具] 查询待缴保费失败 cusUserId={}", cusUserId, e);
            return "缴费查询服务暂时不可用,请稍后再试或联系人工客服。";
        }
    }

    /**
     * 理赔材料清单
     * <p>
     * 材料清单是强业务知识，让模型自由发挥很容易漏项或编造。
     * 这里按理赔类型给出标准清单，模型只负责组织措辞。
     * </p>
     */
    @Tool(name = "getClaimChecklist", description = """
            查询某一类理赔需要准备的材料清单(医疗、重疾、意外、身故)。
            当客户询问"理赔要准备什么材料""报案需要哪些资料""出院了怎么申请理赔"时调用本工具。
            理赔类型取值:MEDICAL-医疗 CRITICAL-重疾 ACCIDENT-意外 LIFE-身故 OTHER-其他/不确定。
            """)
    public String getClaimChecklist(
            @ToolParam(required = false, description = "理赔类型:MEDICAL/CRITICAL/ACCIDENT/LIFE/OTHER") String claimType) {
        try {
            String type = StringUtils.hasText(claimType) ? claimType.trim().toUpperCase() : "OTHER";
            List<String> items = switch (type) {
                case "MEDICAL" -> List.of(
                        "理赔申请书(官网/APP 下载或柜面领取)",
                        "被保险人身份证件",
                        "医疗费用发票原件及费用清单",
                        "出院小结、诊断证明书",
                        "医保结算单(已经社保报销的需提供)",
                        "门诊病历、检查报告等就诊资料",
                        "收款银行卡信息");
                case "CRITICAL" -> List.of(
                        "理赔申请书",
                        "被保险人身份证件",
                        "二级及以上医院出具的诊断证明书",
                        "病理报告、影像学检查、化验报告等确诊依据",
                        "住院病历复印件(含入院记录、手术记录)",
                        "收款银行卡信息");
                case "ACCIDENT" -> List.of(
                        "理赔申请书",
                        "被保险人身份证件",
                        "事故证明(交警责任认定书、单位证明或派出所证明)",
                        "门诊/住院病历与诊断证明",
                        "医疗费用发票及费用清单",
                        "伤残鉴定书(申请伤残责任时提供)",
                        "收款银行卡信息");
                case "LIFE" -> List.of(
                        "理赔申请书",
                        "受益人身份证明及与被保险人的关系证明",
                        "死亡证明、户籍注销证明",
                        "意外身故需提供事故证明",
                        "保险合同原件",
                        "受益人收款账户");
                default -> List.of(
                        "理赔申请书",
                        "被保险人身份证件",
                        "与出险原因相关的诊断证明或事故证明",
                        "医疗费用票据(涉及费用报销时)",
                        "收款银行卡信息");
            };
            StringBuilder text = new StringBuilder();
            text.append(claimTypeName(type)).append("理赔需要准备的材料:\n");
            for (int i = 0; i < items.size(); i++) {
                text.append(i + 1).append(". ").append(items.get(i)).append('\n');
            }
            text.append("""

                    补充说明:
                    - 电子发票与纸质发票均可,但需保证清晰可辨、金额完整;
                    - 材料齐全后可通过官方 APP 上传或前往柜面提交;
                    - 具体材料以保险条款约定和理赔专员告知为准,不同产品可能略有差异。
                    """);
            return text.toString().trim();
        } catch (Exception e) {
            log.warn("[AI工具] 查询理赔材料清单失败 claimType={}", claimType, e);
            return "理赔材料查询服务暂时不可用,请稍后再试或联系人工客服。";
        }
    }

    private String claimTypeName(String type) {
        return switch (type) {
            case "MEDICAL" -> "医疗";
            case "CRITICAL" -> "重疾";
            case "ACCIDENT" -> "意外";
            case "LIFE" -> "身故";
            default -> "一般";
        };
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
