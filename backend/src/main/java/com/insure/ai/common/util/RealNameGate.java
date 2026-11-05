package com.insure.ai.common.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 实名认证问答闸门
 * <p>
 * 未实名客户只允许咨询投保、保全、退保、理赔等<b>通用知识</b>;
 * 一旦问题指向"我自己的"保单/理赔/缴费等业务数据,必须引导先完成实名认证。
 * </p>
 * <p>
 * 这里的正则只负责"命中即拦"的确定性判断,命中后用固定话术直接回复(不调模型,
 * 既省 token 又保证口径一致);正则漏掉的说法由 AI 工具在未实名时拒绝查询兜底,
 * 模型只能引导客户去认证,拿不到任何业务数据。
 * </p>
 *
 * @author shaoyong.wang
 */
public final class RealNameGate {

    /**
     * 指向本人业务数据的问法
     * <p>刻意保持保守:只匹配明确带"我的/本人/我名下"或明确单号的说法,
     * 避免把"退保能退多少钱"这类通用知识问题误拦。</p>
     */
    private static final List<Pattern> PERSONAL_PATTERNS = List.of(
            // 我的保单 / 我名下保单 / 本人的理赔
            Pattern.compile("(我的|本人|我名下|我自己).{0,6}(保单|保额|保费|保障|理赔|赔案|报案|退保|减保|缴费|现金价值)"),
            // 保单是我的 / 理赔记录本人的
            Pattern.compile("(保单|理赔|赔案|缴费|退保|减保|保额).{0,6}(我的|本人|我名下)"),
            // 帮我查一下我的保单 / 查询我名下的理赔
            Pattern.compile("(查|看|查询|核对|确认|算).{0,10}(我的|本人|我名下).{0,12}(保单|理赔|保额|保费|缴费|保障|退保|减保)"),
            // 直接报单号:保单号 P202403010002 / 案号 CLM20261020001
            Pattern.compile("(保单号|理赔案号|案号)\\s*[:：]?\\s*[A-Za-z]{1,4}\\d{6,}"),
            // 我买了什么保险 / 我都投保了哪些
            Pattern.compile("我(买|投保|上)了(什么|哪些)(保险|险种)"),
            // 我是不是忘了交保费 / 我还要交几次保费
            Pattern.compile("我(还|是否|是不是|有没有|要不要|该不该).{0,6}(交|缴)(费|保费)")
    );

    /** 命中后的引导话术(同时用于同步与流式) */
    private static final String GUIDANCE = """
            为保护您的账户与保单信息安全,查询本人保单、理赔、缴费等业务信息需要先完成**实名认证**。

            您可以点击下方「去实名认证」,填写真实姓名与证件号完成认证;认证通过后即可随时查询自己的保单、理赔进度与缴费计划。

            如果您想了解投保、保全、退保、理赔等通用规则,可以直接继续提问,我会正常为您解答。""";

    private RealNameGate() {
    }

    /**
     * 该问题是否必须实名后才能回答
     */
    public static boolean requiresRealName(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return PERSONAL_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(content).find());
    }

    /**
     * 未实名时的引导话术
     */
    public static String guidance() {
        return GUIDANCE;
    }
}
