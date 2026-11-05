package com.insure.ai.modules.kb.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识缺口聚合项
 * <p>同一个问题被问多次时合并成一行，按出现次数排序——次数才是补语料的优先级依据</p>
 *
 * @author insurance-ai
 */
@Data
public class GapGroupVO implements Serializable {

    /** 问题原文(聚合键) */
    private String question;

    /** 出现次数 */
    private Long hitCount;

    /** 该问题下最高的召回得分,0 表示完全没命中 */
    private Integer bestScore;

    /** 状态:0-待处理 1-已转知识 2-已忽略 */
    private Integer status;

    /** 代表记录ID,用于转知识/忽略操作 */
    private Long sampleId;

    /** 最近一次出现时间 */
    private LocalDateTime lastTime;
}
