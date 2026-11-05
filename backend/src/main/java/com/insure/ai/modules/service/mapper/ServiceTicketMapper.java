package com.insure.ai.modules.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.insure.ai.modules.service.entity.ServiceTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 人工工单 Mapper
 *
 * @author insurance-ai
 */
@Mapper
public interface ServiceTicketMapper extends BaseMapper<ServiceTicket> {

    /**
     * 查询指定前缀下最大的工单号，用于生成当日流水
     * <p>原生 SQL 不带逻辑删除条件：已删除的工单号仍占用唯一索引，不能重复分配</p>
     */
    String selectMaxTicketNo(@Param("prefix") String prefix);
}
