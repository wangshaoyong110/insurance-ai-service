package com.insure.ai.modules.service.controller;

import com.insure.ai.aspect.Log;
import com.insure.ai.common.Result;
import com.insure.ai.common.annotation.RateLimit;
import com.insure.ai.common.enums.FeedbackReasonEnum;
import com.insure.ai.common.enums.TicketReasonEnum;
import com.insure.ai.common.util.SecurityUtils;
import com.insure.ai.modules.service.dto.FeedbackDTO;
import com.insure.ai.modules.service.dto.TicketCreateDTO;
import com.insure.ai.modules.service.entity.ChatFeedback;
import com.insure.ai.modules.service.entity.ServiceTicket;
import com.insure.ai.modules.service.service.FeedbackService;
import com.insure.ai.modules.service.service.TicketService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 客户端-服务闭环接口
 * <p>提供 AI 回答评价与转人工工单两个能力,是"AI 答不了时客户去哪"的答案</p>
 *
 * @author insurance-ai
 */
@Tag(name = "客户端-服务闭环", description = "回答评价、转人工工单、我的工单进度")
@RestController
@RequestMapping("/service")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class CusServiceController {

    private final FeedbackService feedbackService;
    private final TicketService ticketService;

    /**
     * 提交回答评价
     * <p>限流 20 次/分钟:评价是轻量写操作,但仍需防脚本刷量污染好评率</p>
     */
    @Log(module = "服务闭环", operation = "评价AI回答")
    @RateLimit(limit = 20, seconds = 60, key = "feedback", message = "操作太频繁了，请稍后再试")
    @PostMapping("/feedback")
    public Result<Void> feedback(@Valid @RequestBody FeedbackDTO dto) {
        feedbackService.submit(SecurityUtils.getUserId(), dto);
        return Result.success();
    }

    /**
     * 查询某条 AI 回复的评价(页面刷新后回显)
     */
    @GetMapping("/feedback/{messageId}")
    public Result<ChatFeedback> feedbackOfMessage(@PathVariable Long messageId) {
        return Result.success(feedbackService.ofMessage(SecurityUtils.getUserId(), messageId));
    }

    /**
     * 差评原因选项(前端弹窗用,避免两端硬编码)
     * <p>路径刻意不写成 {@code /feedback/{messageId}} 的子路径,避免与路径变量产生歧义</p>
     */
    @GetMapping("/feedback-reasons")
    public Result<List<Map<String, String>>> feedbackReasons() {
        return Result.success(java.util.Arrays.stream(FeedbackReasonEnum.values())
                .map(item -> Map.of("code", item.getCode(), "name", item.getDesc()))
                .toList());
    }

    /**
     * 转人工:创建工单
     * <p>限流 5 次/10 分钟:工单要占用坐席人力,不能让单账号刷出几十张空单</p>
     */
    @Log(module = "服务闭环", operation = "转人工")
    @RateLimit(limit = 5, seconds = 600, key = "ticket", message = "提交太频繁了，请稍后再试或等待坐席联系")
    @PostMapping("/tickets")
    public Result<ServiceTicket> createTicket(@Valid @RequestBody TicketCreateDTO dto) {
        return Result.success(ticketService.create(SecurityUtils.getUserId(), dto));
    }

    /**
     * 我的工单列表
     */
    @GetMapping("/tickets")
    public Result<List<ServiceTicket>> myTickets() {
        return Result.success(ticketService.listMine(SecurityUtils.getUserId()));
    }

    /**
     * 取消我的待处理工单
     */
    @PutMapping("/tickets/{id}/cancel")
    public Result<Void> cancelTicket(@PathVariable Long id) {
        ticketService.cancel(SecurityUtils.getUserId(), id);
        return Result.success();
    }

    /**
     * 转人工原因选项
     */
    @GetMapping("/ticket-reasons")
    public Result<List<Map<String, String>>> ticketReasons() {
        return Result.success(java.util.Arrays.stream(TicketReasonEnum.values())
                .map(item -> Map.of("code", item.getCode(), "name", item.getDesc()))
                .toList());
    }
}
