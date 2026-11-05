package com.insure.ai.modules.service.controller;

import com.insure.ai.common.PageResult;
import com.insure.ai.common.Result;
import com.insure.ai.modules.service.dto.FeedbackQuery;
import com.insure.ai.modules.service.entity.ChatFeedback;
import com.insure.ai.modules.service.service.FeedbackService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端-AI 回答评价
 *
 * @author insurance-ai
 */
@Tag(name = "管理端-回答评价", description = "查看客户对 AI 回复的评价与好评率")
@RestController
@RequestMapping("/admin/feedback")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    /**
     * 评价分页(差评优先)
     */
    @GetMapping("/page")
    public Result<PageResult<ChatFeedback>> page(FeedbackQuery query) {
        return Result.success(feedbackService.page(query));
    }

    /**
     * 评价统计
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(feedbackService.statistics());
    }
}
