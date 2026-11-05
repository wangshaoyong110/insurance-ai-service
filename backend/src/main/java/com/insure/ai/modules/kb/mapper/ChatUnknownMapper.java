package com.insure.ai.modules.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.insure.ai.modules.kb.dto.GapGroupVO;
import com.insure.ai.modules.kb.entity.ChatUnknown;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 知识盲区 Mapper
 *
 * @author insurance-ai
 */
@Mapper
public interface ChatUnknownMapper extends BaseMapper<ChatUnknown> {

    /**
     * 按问题聚合分页
     * <p>status 取 MIN：同一问题只要还有未处理的记录，就仍然算待处理，不会被已处理的行掩盖</p>
     */
    IPage<GapGroupVO> selectGapPage(IPage<GapGroupVO> page,
                                   @Param("keyword") String keyword,
                                   @Param("status") Integer status);

    /**
     * 待处理缺口数量(按问题去重)
     */
    Long countPendingGroups();
}
