package com.insure.ai.modules.chat.controller;

import com.insure.ai.aspect.Log;
import com.insure.ai.common.Result;
import com.insure.ai.common.util.SecurityUtils;
import com.insure.ai.modules.chat.dto.ChatMessageSendDTO;
import com.insure.ai.modules.chat.dto.ChatMessageVO;
import com.insure.ai.modules.chat.service.ChatService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端-坐席会话
 * <p>
 * 坐席接单后在同一个会话里与客户沟通:既能看到客户与 AI 的历史对话(知道问题前因后果),
 * 也能直接发文字、图片、视频。会话状态与工单状态由 TicketService 同步维护,
 * 本控制器只负责消息读写。
 * </p>
 *
 * @author insurance-ai
 */
@Tag(name = "管理端-坐席会话", description = "坐席查看会话上下文、回复客户、发送图片与视频")
@RestController
@RequestMapping("/admin/chat")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminChatController {

    private final ChatService chatService;

    /**
     * 会话消息(首次打开返回最近 200 条,之后用 afterId 增量轮询)
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessageVO>> messages(@PathVariable Long sessionId,
                                                @RequestParam(required = false) Long afterId) {
        return Result.success(chatService.adminMessages(sessionId, afterId));
    }

    /**
     * 坐席发送消息(文字 / 图片 / 视频)
     * <p>必须已接单:未接单就发言会让工单状态与会话状态错位</p>
     */
    @Log(module = "坐席会话", operation = "回复客户")
    @PostMapping("/sessions/{sessionId}/send")
    public Result<ChatMessageVO> send(@PathVariable Long sessionId,
                                      @Valid @RequestBody ChatMessageSendDTO dto) {
        String operatorName = SecurityUtils.getLoginUser().getRealName();
        if (operatorName == null || operatorName.isBlank()) {
            operatorName = SecurityUtils.getUsername();
        }
        return Result.success(chatService.staffSend(sessionId, SecurityUtils.getUserId(), operatorName, dto));
    }
}
