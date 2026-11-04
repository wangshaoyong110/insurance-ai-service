package com.insure.ai.modules.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.insure.ai.modules.kb.entity.KbQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 知识库 Mapper
 *
 * @author insurance-ai
 */
@Mapper
public interface KbQuestionMapper extends BaseMapper<KbQuestion> {

    /**
     * 命中次数自增
     *
     * @param id 知识库ID
     */
    @Update("UPDATE kb_question SET hit_count = hit_count + 1 WHERE id = #{id}")
    void increaseHitCount(@Param("id") Long id);
}
