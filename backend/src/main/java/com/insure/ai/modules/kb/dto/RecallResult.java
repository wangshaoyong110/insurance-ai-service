package com.insure.ai.modules.kb.dto;

import com.insure.ai.modules.kb.entity.KbQuestion;

import java.util.List;

/**
 * 知识库召回结果
 * <p>
 * 只返回列表时调用方无法区分"命中了 1 条强相关"和"靠零散二元片段勉强凑够 N 条",
 * 因此把最高分一并带出,供对话链路判断本次回答是否有知识库支撑。
 * </p>
 *
 * @param items     召回条目
 * @param bestScore 最高相关度得分,0 表示完全没命中
 * @author shaoyong.wang
 */
public record RecallResult(List<KbQuestion> items, int bestScore) {
}
