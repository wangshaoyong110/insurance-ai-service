package com.insure.ai.modules.system.controller;

import com.insure.ai.common.Result;
import com.insure.ai.common.enums.UserTypeEnum;
import com.insure.ai.modules.chat.service.ChatService;
import com.insure.ai.modules.system.service.CusUserService;
import com.insure.ai.modules.system.service.SysUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后台首页统计控制器
 * <p>分别统计管理端账号与客户端客户,体现双用户体系</p>
 *
 * @author insurance-ai
 */
@Tag(name = "管理端-数据概览", description = "会话量、客户量、知识库量等统计指标")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final ChatService chatService;
    private final SysUserService sysUserService;
    private final CusUserService cusUserService;

    /**
     * 首页概览数据
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        Map<String, Object> data = new LinkedHashMap<>(chatService.statistics());
        // 管理端账号
        data.put("adminCount", sysUserService.count());
        data.put("adminType", UserTypeEnum.ADMIN.getCode());
        // 客户端客户
        data.put("customerCount", cusUserService.count());
        data.put("enabledCustomerCount", cusUserService.countEnabled());
        data.put("todayCustomerCount", cusUserService.countToday());
        data.put("customerType", UserTypeEnum.CUSTOMER.getCode());
        return Result.success(data);
    }
}
