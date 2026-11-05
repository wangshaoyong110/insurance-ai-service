package com.insure.ai.modules.service.service;

import com.insure.ai.common.PageResult;
import com.insure.ai.modules.service.dto.TicketCreateDTO;
import com.insure.ai.modules.service.dto.TicketHandleDTO;
import com.insure.ai.modules.service.dto.TicketQuery;
import com.insure.ai.modules.service.entity.ServiceTicket;
import java.util.List;
import java.util.Map;

/**
 * 人工工单服务
 * <p>
 * 业务定位:AI 是首道防线,但永远有它处理不了的情况(材料缺失、赔付争议、投诉)。
 * 没有工单时,提示词里那句"请联系人工客服"只是一句空话——客户无处可去,坐席也无从接手。
 * 本服务把这条路补上:客户提单 → 坐席接单 → 回复结单 → 客户可在手机上查看进展。
 * </p>
 *
 * @author insurance-ai
 */
public interface TicketService {

    /**
     * 客户发起转人工
     *
     * @param cusUserId 登录客户ID,由控制器从登录态注入
     */
    ServiceTicket create(Long cusUserId, TicketCreateDTO dto);

    /**
     * 我的工单列表(按创建时间倒序)
     */
    List<ServiceTicket> listMine(Long cusUserId);

    /**
     * 客户取消自己的待处理工单
     * <p>已接单的工单不允许客户单方面取消,避免坐席正在处理时状态被反复打断</p>
     */
    void cancel(Long cusUserId, Long id);

    /**
     * 工单分页(回填客户信息)
     */
    PageResult<ServiceTicket> page(TicketQuery query);

    /**
     * 按筛选条件导出 Excel(xlsx 字节流)
     * <p>条件与列表页一致,不分页,上限 5 万条;手机号脱敏输出</p>
     */
    byte[] exportExcel(TicketQuery query);

    /**
     * 工单详情
     */
    ServiceTicket detail(Long id);

    /**
     * 流转工单:接单 / 结单 / 取消
     *
     * @param operatorId   当前坐席ID
     * @param operatorName 当前坐席姓名
     */
    ServiceTicket handle(Long id, TicketHandleDTO dto, Long operatorId, String operatorName);

    /**
     * 工单统计:看板与工单页顶部卡片共用
     */
    Map<String, Object> statistics();
}
