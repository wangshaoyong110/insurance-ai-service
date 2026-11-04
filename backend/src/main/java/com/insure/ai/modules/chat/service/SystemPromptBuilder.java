package com.insure.ai.modules.chat.service;

import com.insure.ai.common.enums.InsuranceCategoryEnum;
import com.insure.ai.modules.kb.entity.KbQuestion;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 系统提示词构建器
 * <p>把角色设定、合规要求与知识库召回结果组织成 system 消息,保证回答可控可解释</p>
 *
 * @author insurance-ai
 */
@Component
public class SystemPromptBuilder {

    /** 基础人设与合规约束 */
    private static final String BASE_PROMPT = """
            你是「安鑫保险」的智能客服助手,服务对象是已经注册的保险客户。
            请严格遵守以下规则:
            1. 只回答投保、保全、退保、减保、理赔、保单服务等保险相关问题;与业务无关的问题请礼貌拒答并引导回保险话题。
            2. 严格保护客户隐私:绝不索要、复述、推断客户的身份证号、银行卡号、保单号、密码、短信验证码、家庭住址等敏感信息。
               如客户主动提供上述信息,应提醒其注意信息安全,并建议在官方渠道办理业务。
            3. 不得承诺任何未在条款中约定的收益或赔付结果;涉及具体保单的现金价值、赔付金额、核保结论等必须以系统查询或人工客服核实为准,
               可引导客户提供保单号后转人工客服处理。
            4. 内容不确定时必须明确说明"该问题需人工客服进一步核实",严禁编造条款、比例、时限。
            5. 回答使用简体中文,条理清晰,分点说明;单次回答控制在 300 字以内,语气专业、温和、易懂。
            6. 涉及退保、减保等可能造成客户损失的操作,需提示客户注意犹豫期、现金价值及保障中断风险。

            你可以调用以下工具查询当前登录客户的真实业务数据:
            - listMyPolicies:查询本人名下保单清单
            - getPolicyDetail:按保单号查询本人某张保单详情
            - listMyClaims:查询本人理赔记录
            - getClaimProgress:按理赔案号查询本人理赔进度

            工具使用规则(必须遵守):
            7. 客户询问"我的保单""我买了什么保险""保额多少""什么时候到期""我的理赔""赔了多少钱""进度到哪一步"等
               自身业务数据时,必须先调用对应工具拿到真实数据再回答,严禁凭推测编造保单号、保额、赔付金额与时间。
            8. 工具只能查询当前登录客户本人的数据,无需让客户提供身份证号或保单号;若客户要求查询配偶、父母、朋友等
               他人保单或理赔,应说明只能查询本人名下信息,建议由本人登录或前往柜面办理。
            9. 工具返回的内容是事实依据:金额、日期、状态必须与工具结果一致。工具报错或查无数据时如实说明,
               不得猜测,可建议客户核对单号或联系人工客服。
            """;

    /** 知识库上下文模板 */
    private static final String KNOWLEDGE_TEMPLATE = """

            【以下是公司知识库中的标准问答,回答时请优先参考,不要与之一致性冲突】
            %s
            """;

    /**
     * 构建 system 提示词
     *
     * @param references 知识库召回结果,可为空
     */
    public String build(List<KbQuestion> references) {
        StringBuilder prompt = new StringBuilder(BASE_PROMPT);
        if (references == null || references.isEmpty()) {
            return prompt.toString();
        }
        StringBuilder knowledge = new StringBuilder();
        int index = 1;
        for (KbQuestion item : references) {
            knowledge.append(index++)
                    .append(". [")
                    .append(InsuranceCategoryEnum.descOf(item.getCategory()))
                    .append("] 问: ")
                    .append(item.getQuestion())
                    .append(" 答: ")
                    .append(item.getAnswer())
                    .append('\n');
        }
        if (StringUtils.hasText(knowledge)) {
            prompt.append(KNOWLEDGE_TEMPLATE.formatted(knowledge));
        }
        return prompt.toString();
    }
}
