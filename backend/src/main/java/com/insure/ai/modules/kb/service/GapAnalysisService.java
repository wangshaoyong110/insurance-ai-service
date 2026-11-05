package com.insure.ai.modules.kb.service;

import com.insure.ai.common.PageResult;
import com.insure.ai.modules.kb.dto.GapGroupVO;
import java.util.List;
import java.util.Map;

/**
 * 知识缺口分析服务
 * <p>
 * 解决的问题:知识库只能靠人拍脑袋补。客户问了什么而系统答不上来，没人知道，
 * 于是同一个问题被反复问、反复答不上来。
 * </p>
 * <p>
 * 闭环设计:对话命中弱 → 落一条盲区记录 → 后台按问题聚合、按次数排序 →
 * 运营补一条标准问答 → 下次同样的问题就能命中。知识库因此能自己长出来。
 * </p>
 *
 * @author insurance-ai
 */
public interface GapAnalysisService {

    /**
     * 记录一条知识盲区
     * <p>
     * 只记录"召回为空"或"最高分低于关键词权重"的问题——那是知识库真的没覆盖到的部分。
     * 同一客户重复问同一句只留一条待处理记录，避免一次追问把统计刷成 5 次。
     * </p>
     *
     * @param bestScore 本次召回的最高得分
     */
    void record(Long cusUserId, Long sessionId, String question, int bestScore);

    /**
     * 缺口分页(按问题聚合)
     */
    PageResult<GapGroupVO> page(String keyword, Integer status, long pageNum, long pageSize);

    /**
     * 把某个缺口问题转成知识库条目
     * <p>同一问题的全部待处理记录一并标记为已转，避免补完语料后列表里还挂着旧记录</p>
     *
     * @param id       代表记录ID
     * @param category 业务分类
     * @param answer   标准答案
     * @param keywords 关键词
     */
    Long toKnowledge(Long id, String category, String answer, String keywords);

    /**
     * 忽略某个缺口问题
     */
    void ignore(Long id);

    /**
     * 缺口统计:待处理问题数、今日新增、最高频问题
     */
    Map<String, Object> statistics();
}
