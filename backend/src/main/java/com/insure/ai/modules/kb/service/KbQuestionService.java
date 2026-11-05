package com.insure.ai.modules.kb.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.Constants;
import com.insure.ai.common.PageResult;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.CommonStatusEnum;
import com.insure.ai.modules.kb.dto.KbQuery;
import com.insure.ai.modules.kb.dto.KbSaveDTO;
import com.insure.ai.modules.kb.entity.KbQuestion;
import com.insure.ai.modules.kb.mapper.KbQuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 知识库服务
 * <p>启用状态的知识库会缓存到 Redis,减少高频对话下的数据库压力</p>
 *
 * @author insurance-ai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbQuestionService {

    /** 关键词命中权重 */
    private static final int KEYWORD_WEIGHT = 3;
    /** 问题片段命中权重 */
    private static final int FRAGMENT_WEIGHT = 1;
    /** 问题片段长度 */
    private static final int FRAGMENT_LENGTH = 2;
    /** 默认召回条数 */
    private static final int DEFAULT_RECALL_LIMIT = 3;

    private final KbQuestionMapper kbQuestionMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 分页查询知识库
     */
    public PageResult<KbQuestion> page(KbQuery query) {
        Page<KbQuestion> page = new Page<>(query.getPageNum(), query.getPageSize());
        kbQuestionMapper.selectPage(page, Wrappers.<KbQuestion>lambdaQuery()
                .eq(StringUtils.hasText(query.getCategory()), KbQuestion::getCategory, query.getCategory())
                .eq(query.getStatus() != null, KbQuestion::getStatus, query.getStatus())
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                        .like(KbQuestion::getQuestion, query.getKeyword())
                        .or().like(KbQuestion::getAnswer, query.getKeyword())
                        .or().like(KbQuestion::getKeywords, query.getKeyword()))
                .orderByDesc(KbQuestion::getId));
        return PageResult.of(page);
    }

    /**
     * 查询全部启用的知识库(带 Redis 缓存)
     */
    public List<KbQuestion> listEnabled() {
        String cached = stringRedisTemplate.opsForValue().get(Constants.REDIS_KB_CACHE);
        if (StringUtils.hasText(cached)) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<KbQuestion>>() {
                });
            } catch (Exception e) {
                log.warn("[知识库] 缓存解析失败,回退到数据库查询: {}", e.getMessage());
            }
        }
        List<KbQuestion> list = kbQuestionMapper.selectList(Wrappers.<KbQuestion>lambdaQuery()
                .eq(KbQuestion::getStatus, CommonStatusEnum.ENABLE.getCode())
                .orderByDesc(KbQuestion::getHitCount));
        try {
            stringRedisTemplate.opsForValue().set(Constants.REDIS_KB_CACHE,
                    objectMapper.writeValueAsString(list), Duration.ofSeconds(Constants.KB_CACHE_SECONDS));
        } catch (Exception e) {
            log.warn("[知识库] 缓存写入失败: {}", e.getMessage());
        }
        return list;
    }

    /**
     * 根据用户问题召回最相关的知识库条目
     *
     * @param content 用户问题
     * @param limit   召回条数
     */
    public List<KbQuestion> recall(String content, int limit) {
        return recallWithScore(content, limit).items();
    }

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
    public RecallResult recallWithScore(String content, int limit) {
        if (!StringUtils.hasText(content)) {
            return new RecallResult(List.of(), 0);
        }
        List<KbQuestion> all = listEnabled();
        int max = limit <= 0 ? DEFAULT_RECALL_LIMIT : limit;
        List<Map.Entry<KbQuestion, Integer>> scored = all.stream()
                .map(item -> Map.entry(item, score(content, item)))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<KbQuestion, Integer>comparingByValue(Comparator.reverseOrder()))
                .toList();
        List<KbQuestion> items = scored.stream()
                .limit(max)
                .map(Map.Entry::getKey)
                .toList();
        int bestScore = scored.isEmpty() ? 0 : scored.get(0).getValue();
        return new RecallResult(items, bestScore);
    }

    /**
     * 弱命中阈值:达到该分数说明至少命中了一个关键词，才算知识库真的覆盖了这个问题
     */
    public static int weakScoreThreshold() {
        return KEYWORD_WEIGHT;
    }

    /**
     * 召回结果:条目 + 最高得分
     *
     * @param items     召回条目
     * @param bestScore 最高相关度得分,0 表示完全没命中
     */
    public record RecallResult(List<KbQuestion> items, int bestScore) {
    }

    /**
     * 计算相关度得分:关键词命中 + 问题二元片段命中
     */
    private int score(String content, KbQuestion item) {
        int score = 0;
        if (StringUtils.hasText(item.getKeywords())) {
            for (String keyword : item.getKeywords().split(",")) {
                String trimmed = keyword.trim();
                if (!trimmed.isEmpty() && content.contains(trimmed)) {
                    score += KEYWORD_WEIGHT;
                }
            }
        }
        String question = item.getQuestion() == null ? "" : item.getQuestion();
        List<String> fragments = new ArrayList<>();
        for (int i = 0; i + FRAGMENT_LENGTH <= question.length(); i++) {
            fragments.add(question.substring(i, i + FRAGMENT_LENGTH));
        }
        for (String fragment : fragments) {
            if (content.contains(fragment)) {
                score += FRAGMENT_WEIGHT;
            }
        }
        return score;
    }

    /**
     * 新增或更新知识库
     */
    @Transactional(rollbackFor = Exception.class)
    public Long save(KbSaveDTO dto) {
        KbQuestion entity = new KbQuestion();
        entity.setId(dto.getId());
        entity.setCategory(dto.getCategory());
        entity.setQuestion(dto.getQuestion());
        entity.setAnswer(dto.getAnswer());
        entity.setKeywords(dto.getKeywords());
        entity.setStatus(dto.getStatus() == null ? CommonStatusEnum.ENABLE.getCode() : dto.getStatus());
        entity.setRemark(dto.getRemark());
        if (dto.getId() == null) {
            entity.setHitCount(0);
            kbQuestionMapper.insert(entity);
        } else {
            KbQuestion exists = kbQuestionMapper.selectById(dto.getId());
            if (exists == null) {
                throw new BusinessException(ResultCode.NOT_FOUND);
            }
            kbQuestionMapper.updateById(entity);
        }
        evictCache();
        return entity.getId();
    }

    /**
     * 逻辑删除知识库
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        kbQuestionMapper.deleteById(id);
        evictCache();
    }

    /**
     * 命中次数自增(异步场景由调用方保证不阻塞主流程)
     */
    public void increaseHitCount(Long id) {
        kbQuestionMapper.increaseHitCount(id);
    }

    /**
     * 清理知识库缓存
     */
    public void evictCache() {
        stringRedisTemplate.delete(Constants.REDIS_KB_CACHE);
    }
}
