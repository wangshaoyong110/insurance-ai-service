package com.insure.ai.modules.service.controller;

import com.insure.ai.aspect.Log;
import com.insure.ai.common.PageResult;
import com.insure.ai.common.Result;
import com.insure.ai.common.util.SecurityUtils;
import com.insure.ai.modules.service.dto.TicketHandleDTO;
import com.insure.ai.modules.service.dto.TicketQuery;
import com.insure.ai.modules.service.entity.ServiceTicket;
import com.insure.ai.modules.service.service.TicketService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端-服务工单
 *
 * @author insurance-ai
 */
@Tag(name = "管理端-服务工单", description = "转人工工单的查询、接单与结单")
@RestController
@RequestMapping("/admin/ticket")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminTicketController {

    private final TicketService ticketService;

    /**
     * 工单分页(待处理优先)
     */
    @GetMapping("/page")
    public Result<PageResult<ServiceTicket>> page(TicketQuery query) {
        return Result.success(ticketService.page(query));
    }

    /**
     * 工单详情
     */
    @GetMapping("/{id}")
    public Result<ServiceTicket> detail(@PathVariable Long id) {
        return Result.success(ticketService.detail(id));
    }

    /**
     * 工单流转:接单 / 结单 / 取消
     */
    @Log(module = "服务工单", operation = "处理工单")
    @PutMapping("/{id}/handle")
    public Result<ServiceTicket> handle(@PathVariable Long id, @Valid @RequestBody TicketHandleDTO dto) {
        String operatorName = SecurityUtils.getLoginUser().getRealName();
        if (operatorName == null || operatorName.isBlank()) {
            operatorName = SecurityUtils.getUsername();
        }
        return Result.success(ticketService.handle(id, dto, SecurityUtils.getUserId(), operatorName));
    }

    /**
     * 工单统计
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(ticketService.statistics());
    }
}
