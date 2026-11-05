package com.insure.ai.modules.service.service.impl;

import com.insure.ai.modules.service.service.TicketService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.PageResult;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.TicketReasonEnum;
import com.insure.ai.common.enums.TicketStatusEnum;
import com.insure.ai.modules.service.dto.TicketCreateDTO;
import com.insure.ai.modules.service.dto.TicketHandleDTO;
import com.insure.ai.modules.service.dto.TicketQuery;
import com.insure.ai.modules.service.entity.ServiceTicket;
import com.insure.ai.modules.service.mapper.ServiceTicketMapper;
import com.insure.ai.modules.system.entity.CusUser;
import com.insure.ai.modules.system.mapper.CusUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    /** 工单号前缀 */
    private static final String TICKET_PREFIX = "TK";
    /** 日期格式(工单号中段) */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 单号生成冲突时的最大重试次数 */
    private static final int MAX_RETRY = 3;
    /** 客户侧最多展示的历史工单条数 */
    private static final int CUSTOMER_TICKET_LIMIT = 50;

    private final ServiceTicketMapper ticketMapper;
    private final CusUserMapper cusUserMapper;

    // ==================================================================
    // 客户侧
    // ==================================================================

    /**
     * 客户发起转人工
     *
     * @param cusUserId 登录客户ID,由控制器从登录态注入
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public ServiceTicket create(Long cusUserId, TicketCreateDTO dto) {
        ServiceTicket ticket = new ServiceTicket();
        ticket.setCusUserId(cusUserId);
        ticket.setSessionId(dto.getSessionId());
        ticket.setQuestion(dto.getQuestion().trim());
        ticket.setCategory(StringUtils.hasText(dto.getCategory()) ? dto.getCategory() : "OTHER");
        ticket.setReason(TicketReasonEnum.of(dto.getReason()).getCode());
        ticket.setStatus(TicketStatusEnum.PENDING.getCode());
        ticket.setPriority(dto.getPriority() == null ? 1 : Math.min(Math.max(dto.getPriority(), 1), 2));
        insertWithTicketNo(ticket);
        log.info("[工单] 客户提交工单 no={} cusUserId={} reason={}",
                ticket.getTicketNo(), cusUserId, ticket.getReason());
        return ticket;
    }

    /**
     * 我的工单列表(按创建时间倒序)
     */
    @Override
    public List<ServiceTicket> listMine(Long cusUserId) {
        return ticketMapper.selectList(Wrappers.<ServiceTicket>lambdaQuery()
                .eq(ServiceTicket::getCusUserId, cusUserId)
                .orderByDesc(ServiceTicket::getId)
                .last("LIMIT " + CUSTOMER_TICKET_LIMIT));
    }

    /**
     * 客户取消自己的待处理工单
     * <p>已接单的工单不允许客户单方面取消,避免坐席正在处理时状态被反复打断</p>
     */
    @Override
    public void cancel(Long cusUserId, Long id) {
        ServiceTicket ticket = requireOwn(cusUserId, id);
        if (!TicketStatusEnum.PENDING.getCode().equals(ticket.getStatus())) {
            throw new BusinessException(ResultCode.TICKET_STATUS_INVALID.getCode(), "工单已被受理,无法取消");
        }
        ServiceTicket update = new ServiceTicket();
        update.setId(id);
        update.setStatus(TicketStatusEnum.CANCELED.getCode());
        update.setFinishTime(LocalDateTime.now());
        ticketMapper.updateById(update);
        log.info("[工单] 客户取消工单 no={} cusUserId={}", ticket.getTicketNo(), cusUserId);
    }

    // ==================================================================
    // 管理端
    // ==================================================================

    /**
     * 工单分页(回填客户信息)
     */
    @Override
    public PageResult<ServiceTicket> page(TicketQuery query) {
        List<Long> customerIds = null;
        if (StringUtils.hasText(query.getCustomer())) {
            customerIds = matchCustomerIds(query.getCustomer());
            if (customerIds.isEmpty()) {
                return PageResult.empty(query.getPageNum(), query.getPageSize());
            }
        }
        Page<ServiceTicket> page = new Page<>(query.getPageNum(), query.getPageSize());
        ticketMapper.selectPage(page, Wrappers.<ServiceTicket>lambdaQuery()
                .like(StringUtils.hasText(query.getTicketNo()), ServiceTicket::getTicketNo, query.getTicketNo())
                .eq(query.getStatus() != null, ServiceTicket::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getReason()), ServiceTicket::getReason, query.getReason())
                .in(customerIds != null, ServiceTicket::getCusUserId, customerIds)
                .ge(StringUtils.hasText(query.getStartDate()), ServiceTicket::getCreateTime,
                        parseStart(query.getStartDate()))
                .le(StringUtils.hasText(query.getEndDate()), ServiceTicket::getCreateTime,
                        parseEnd(query.getEndDate()))
                // 待处理优先,其次按紧急程度与新单时间,坐席打开列表即可看到最该先动手的
                .orderByAsc(ServiceTicket::getStatus)
                .orderByDesc(ServiceTicket::getPriority)
                .orderByDesc(ServiceTicket::getId));
        PageResult<ServiceTicket> result = PageResult.of(page, item -> item);
        fillCustomer(result.getRecords());
        return result;
    }

    /**
     * 工单详情
     */
    @Override
    public ServiceTicket detail(Long id) {
        ServiceTicket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(ResultCode.TICKET_NOT_EXIST);
        }
        fillCustomer(List.of(ticket));
        return ticket;
    }

    /**
     * 流转工单:接单 / 结单 / 取消
     *
     * @param operatorId   当前坐席ID
     * @param operatorName 当前坐席姓名
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public ServiceTicket handle(Long id, TicketHandleDTO dto, Long operatorId, String operatorName) {
        ServiceTicket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(ResultCode.TICKET_NOT_EXIST);
        }
        TicketStatusEnum target = TicketStatusEnum.of(dto.getStatus());
        if (target == TicketStatusEnum.PENDING) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不能把工单退回待处理");
        }
        if (TicketStatusEnum.of(ticket.getStatus()).isClosed()) {
            throw new BusinessException(ResultCode.TICKET_STATUS_INVALID.getCode(), "工单已结束,无法再次流转");
        }
        if (target == TicketStatusEnum.FINISHED && !StringUtils.hasText(dto.getReply())) {
            // 结单必须给出客户看得见的答复,否则客户只会看到"已完成"却不知道结果
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "结单前请填写给客户的回复");
        }

        ServiceTicket update = new ServiceTicket();
        update.setId(id);
        update.setStatus(target.getCode());
        update.setHandlerId(operatorId);
        update.setHandlerName(operatorName);
        if (StringUtils.hasText(dto.getReply())) {
            update.setReply(dto.getReply().trim());
        }
        if (dto.getPriority() != null) {
            update.setPriority(dto.getPriority());
        }
        if (target == TicketStatusEnum.PROCESSING && ticket.getAcceptTime() == null) {
            update.setAcceptTime(LocalDateTime.now());
        }
        if (target == TicketStatusEnum.FINISHED || target == TicketStatusEnum.CANCELED) {
            update.setFinishTime(LocalDateTime.now());
        }
        ticketMapper.updateById(update);
        log.info("[工单] 状态流转 no={} {} -> {} 操作人={}",
                ticket.getTicketNo(), TicketStatusEnum.descOf(ticket.getStatus()),
                target.getDesc(), operatorName);
        return detail(id);
    }

    /**
     * 工单统计:看板与工单页顶部卡片共用
     */
    @Override
    public Map<String, Object> statistics() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long pending = count(TicketStatusEnum.PENDING.getCode(), null);
        long processing = count(TicketStatusEnum.PROCESSING.getCode(), null);
        long finished = count(TicketStatusEnum.FINISHED.getCode(), null);
        long todayNew = ticketMapper.selectCount(Wrappers.<ServiceTicket>lambdaQuery()
                .ge(ServiceTicket::getCreateTime, startOfDay));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pending", pending);
        data.put("processing", processing);
        data.put("finished", finished);
        data.put("todayNew", todayNew);
        // 人工接手率:有多少工单真的被坐席接走过,是最直接的"AI 兜底能力"指标
        long total = ticketMapper.selectCount(null);
        data.put("acceptRate", total == 0 ? 0 : Math.round((processing + finished) * 100.0 / total));
        return data;
    }

    // ==================================================================
    // 内部方法
    // ==================================================================

    private long count(Integer status, LocalDateTime from) {
        return ticketMapper.selectCount(Wrappers.<ServiceTicket>lambdaQuery()
                .eq(ServiceTicket::getStatus, status)
                .ge(from != null, ServiceTicket::getCreateTime, from));
    }

    /**
     * 生成当日流水号并落库
     * <p>并发下两个请求可能算出同一个单号，靠唯一索引兜底后重试，避免加分布式锁</p>
     */
    private void insertWithTicketNo(ServiceTicket ticket) {
        String prefix = TICKET_PREFIX + LocalDate.now().format(DATE_FORMAT);
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            ticket.setTicketNo(nextTicketNo(prefix));
            try {
                ticketMapper.insert(ticket);
                return;
            } catch (DuplicateKeyException e) {
                log.warn("[工单] 单号冲突,第 {} 次重试 no={}", attempt, ticket.getTicketNo());
            }
        }
        throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(), "工单号生成失败,请稍后重试");
    }

    private String nextTicketNo(String prefix) {
        String max = ticketMapper.selectMaxTicketNo(prefix);
        int seq = 1;
        if (StringUtils.hasText(max) && max.length() > prefix.length()) {
            try {
                seq = Integer.parseInt(max.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                log.warn("[工单] 单号解析失败 max={},从 1 重新开始", max);
            }
        }
        return prefix + String.format("%04d", seq);
    }

    private ServiceTicket requireOwn(Long cusUserId, Long id) {
        ServiceTicket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(ResultCode.TICKET_NOT_EXIST);
        }
        if (!Objects.equals(ticket.getCusUserId(), cusUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return ticket;
    }

    /**
     * 回填客户展示名与手机号
     */
    private void fillCustomer(List<ServiceTicket> tickets) {
        if (tickets.isEmpty()) {
            return;
        }
        Map<Long, CusUser> users = loadUsers(tickets.stream()
                .map(ServiceTicket::getCusUserId).filter(Objects::nonNull).collect(Collectors.toSet()));
        for (ServiceTicket ticket : tickets) {
            CusUser user = users.get(ticket.getCusUserId());
            if (user != null) {
                ticket.setCustomerName(StringUtils.hasText(user.getRealName())
                        ? user.getRealName() : user.getNickname());
                ticket.setCustomerPhone(user.getPhone());
            }
        }
    }

    private Map<Long, CusUser> loadUsers(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return cusUserMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(CusUser::getId, user -> user, (a, b) -> a));
    }

    /**
     * 按姓名/手机号匹配客户ID(与保单查询保持一致的模糊匹配口径)
     */
    private List<Long> matchCustomerIds(String keyword) {
        return cusUserMapper.selectList(Wrappers.<CusUser>lambdaQuery()
                        .and(wrapper -> wrapper
                                .like(CusUser::getNickname, keyword)
                                .or().like(CusUser::getRealName, keyword)
                                .or().like(CusUser::getPhone, keyword)))
                .stream()
                .map(CusUser::getId)
                .toList();
    }

    private LocalDateTime parseStart(String date) {
        return StringUtils.hasText(date) ? LocalDate.parse(date).atStartOfDay() : null;
    }

    private LocalDateTime parseEnd(String date) {
        return StringUtils.hasText(date) ? LocalDate.parse(date).atTime(23, 59, 59) : null;
    }
}
