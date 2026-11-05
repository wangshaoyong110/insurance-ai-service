package com.insure.ai.modules.kb.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.PageResult;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.InsuranceCategoryEnum;
import com.insure.ai.modules.kb.dto.GapGroupVO;
import com.insure.ai.modules.kb.dto.KbSaveDTO;
import com.insure.ai.modules.kb.entity.ChatUnknown;
import com.insure.ai.modules.kb.mapper.ChatUnknownMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.LinkedHashMap;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class GapAnalysisService {

    /** 盲区问题最大保存长度,与表字段一致 */
    private static final int QUESTION_MAX_LENGTH = 500;
    /** 待处理状态 */
    private static final int STATUS_PENDING = 0;
    /** 已转知识 */
    private static final int STATUS_CONVERTED = 1;
    /** 已忽略 */
    private static final int STATUS_IGNORED = 2;

    private final ChatUnknownMapper unknownMapper;
    private final KbQuestionService kbQuestionService;

    /**
     * 记录一条知识盲区
     * <p>
     * 只记录"召回为空"或"最高分低于关键词权重"的问题——那是知识库真的没覆盖到的部分。
     * 同一客户重复问同一句只留一条待处理记录，避免一次追问把统计刷成 5 次。
     * </p>
     *
     * @param bestScore 本次召回的最高得分
     */
    public void record(Long cusUserId, Long sessionId, String question, int bestScore) {
        if (!StringUtils.hasText(question)) {
            return;
        }
        String text = question.trim();
        if (text.length() > QUESTION_MAX_LENGTH) {
            text = text.substring(0, QUESTION_MAX_LENGTH);
        }
        try {
            Long exists = unknownMapper.selectCount(Wrappers.<ChatUnknown>lambdaQuery()
                    .eq(ChatUnknown::getCusUserId, cusUserId)
                    .eq(ChatUnknown::getQuestion, text)
                    .eq(ChatUnknown::getStatus, STATUS_PENDING));
            if (exists != null && exists > 0) {
                return;
            }
            ChatUnknown entity = new ChatUnknown();
            entity.setCusUserId(cusUserId);
            entity.setSessionId(sessionId);
            entity.setQuestion(text);
            entity.setBestScore(Math.max(bestScore, 0));
            entity.setStatus(STATUS_PENDING);
            unknownMapper.insert(entity);
        } catch (Exception e) {
            // 埋点性质的数据不能影响正常对话
            log.warn("[知识缺口] 记录失败 question={} 原因={}", text, e.getMessage());
        }
    }

    /**
     * 缺口分页(按问题聚合)
     */
    public PageResult<GapGroupVO> page(String keyword, Integer status, long pageNum, long pageSize) {
        IPage<GapGroupVO> page = unknownMapper.selectGapPage(new Page<>(pageNum, pageSize), keyword, status);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 把某个缺口问题转成知识库条目
     * <p>同一问题的全部待处理记录一并标记为已转，避免补完语料后列表里还挂着旧记录</p>
     *
     * @param id       代表记录ID
     * @param category 业务分类
     * @param answer   标准答案
     * @param keywords 关键词
     */
    @Transactional(rollbackFor = Exception.class)
    public Long toKnowledge(Long id, String category, String answer, String keywords) {
        ChatUnknown record = unknownMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "缺口记录不存在");
        }
        if (!StringUtils.hasText(answer)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请填写标准答案");
        }

        KbSaveDTO dto = new KbSaveDTO();
        dto.setCategory(InsuranceCategoryEnum.of(category).getCode());
        dto.setQuestion(record.getQuestion());
        dto.setAnswer(answer.trim());
        dto.setKeywords(StringUtils.hasText(keywords) ? keywords.trim() : null);
        // 由缺口转化来的知识直接启用：运营是照着真实提问补的，可信度高于随手新增
        dto.setStatus(1);
        dto.setRemark("来自知识缺口：" + record.getQuestion());
        Long kbId = kbQuestionService.save(dto);

        markSameQuestion(record.getQuestion(), STATUS_CONVERTED, kbId);
        log.info("[知识缺口] 已转为知识 kbId={} question={}", kbId, record.getQuestion());
        return kbId;
    }

    /**
     * 忽略某个缺口问题
     */
    @Transactional(rollbackFor = Exception.class)
    public void ignore(Long id) {
        ChatUnknown record = unknownMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "缺口记录不存在");
        }
        markSameQuestion(record.getQuestion(), STATUS_IGNORED, null);
        log.info("[知识缺口] 已忽略 question={}", record.getQuestion());
    }

    /**
     * 缺口统计:待处理问题数、今日新增、最高频问题
     */
    public Map<String, Object> statistics() {
        Long pendingGroups = unknownMapper.countPendingGroups();
        Long todayRecords = unknownMapper.selectCount(Wrappers.<ChatUnknown>lambdaQuery()
                .ge(ChatUnknown::getCreateTime, LocalDate.now().atStartOfDay()));
        Long totalRecords = unknownMapper.selectCount(null);

        PageResult<GapGroupVO> top = page(null, STATUS_PENDING, 1, 5);
        List<String> topQuestions = top.getRecords().stream()
                .map(GapGroupVO::getQuestion)
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pendingGroups", pendingGroups);
        data.put("todayRecords", todayRecords);
        data.put("totalRecords", totalRecords);
        data.put("topQuestions", topQuestions);
        return data;
    }

    /**
     * 把同一问题的待处理记录批量标记为同一种处理结果
     */
    private void markSameQuestion(String question, int status, Long kbId) {
        ChatUnknown update = new ChatUnknown();
        update.setStatus(status);
        update.setKbId(kbId);
        unknownMapper.update(update, Wrappers.<ChatUnknown>lambdaUpdate()
                .eq(ChatUnknown::getQuestion, question)
                .eq(ChatUnknown::getStatus, STATUS_PENDING));
    }
}
