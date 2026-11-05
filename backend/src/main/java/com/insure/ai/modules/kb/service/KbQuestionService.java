package com.insure.ai.modules.kb.service;

import com.insure.ai.common.PageResult;
import com.insure.ai.modules.kb.dto.KbQuery;
import com.insure.ai.modules.kb.dto.KbSaveDTO;
import com.insure.ai.modules.kb.dto.RecallResult;
import com.insure.ai.modules.kb.entity.KbQuestion;
import java.util.List;
import java.util.Map;

/**
 * 知识库服务
 * <p>启用状态的知识库会缓存到 Redis,减少高频对话下的数据库压力</p>
 *
 * @author insurance-ai
 */
public interface KbQuestionService {

    /**
     * 分页查询知识库
     */
    PageResult<KbQuestion> page(KbQuery query);

    /**
     * 查询全部启用的知识库(带 Redis 缓存)
     */
    List<KbQuestion> listEnabled();

    /**
     * 根据用户问题召回最相关的知识库条目
     *
     * @param content 用户问题
     * @param limit   召回条数
     */
    List<KbQuestion> recall(String content, int limit);

    /**
     * 召回并同时返回最高得分
     * <p>
     * 只返回列表时调用方无法区分"命中了 1 条强相关"和"靠零散二元片段勉强凑够 3 条"。
     * 把最高分带出来，对话链路才能判断这次回答到底有没有知识库支撑，
     * 进而决定是否记为知识盲区。
     * </p>
     *
     * @param content 用户问题
     * @param limit   召回条数
     */
    RecallResult recallWithScore(String content, int limit);

    /**
     * 新增或更新知识库
     */
    Long save(KbSaveDTO dto);

    /**
     * 逻辑删除知识库
     */
    void delete(Long id);

    /**
     * 命中次数自增(异步场景由调用方保证不阻塞主流程)
     */
    void increaseHitCount(Long id);

    /**
     * 清理知识库缓存
     */
    void evictCache();

    /**
     * 关键词命中一次的权重,同时作为"弱命中"阈值
     * <p>达到该分数说明至少命中一个关键词,才算知识库真的覆盖了这个问题</p>
     */
    int WEAK_SCORE_THRESHOLD = 3;

    /**
     * 弱命中阈值
     *
     * @return 阈值分数
     */
    static int weakScoreThreshold() {
        return WEAK_SCORE_THRESHOLD;
    }
}
