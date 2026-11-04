package com.insure.ai.modules.chat.controller;

import com.insure.ai.aspect.Log;
import com.insure.ai.common.Result;
import com.insure.ai.common.enums.InsuranceCategoryEnum;
import com.insure.ai.common.util.SecurityUtils;
import com.insure.ai.modules.chat.dto.ChatAnswerVO;
import com.insure.ai.modules.chat.dto.ChatMessageVO;
import com.insure.ai.modules.chat.dto.ChatSendDTO;
import com.insure.ai.modules.chat.dto.ChatSessionVO;
import com.insure.ai.modules.chat.service.ChatService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 智能客服控制器(客户端使用)
 *
 * @author insurance-ai
 */
@Tag(name = "客户端-智能咨询", description = "客户发起提问、查询历史会话、查看会话消息")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 历史会话列表
     */
    @GetMapping("/sessions")
    public Result<List<ChatSessionVO>> sessions() {
        return Result.success(chatService.listSessions(SecurityUtils.getUserId()));
    }

    /**
     * 新建空会话
     */
    @PostMapping("/sessions")
    public Result<ChatSessionVO> createSession(@RequestParam(required = false) String title) {
        return Result.success(chatService.createSession(SecurityUtils.getUserId(), title));
    }

    /**
     * 删除会话
     */
    @Log(module = "智能客服", operation = "删除会话")
    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(@PathVariable Long id) {
        chatService.deleteSession(SecurityUtils.getUserId(), id);
        return Result.success();
    }

    /**
     * 重命名会话
     */
    @PutMapping("/sessions/{id}")
    public Result<Void> renameSession(@PathVariable Long id, @RequestParam String title) {
        chatService.renameSession(SecurityUtils.getUserId(), id, title);
        return Result.success();
    }

    /**
     * 查询会话消息(历史查询)
     */
    @GetMapping("/sessions/{id}/messages")
    public Result<List<ChatMessageVO>> messages(@PathVariable Long id) {
        return Result.success(chatService.listMessages(SecurityUtils.getUserId(), id));
    }

    /**
     * 发送消息并获取 AI 回答
     */
    @Log(module = "智能客服", operation = "AI 对话")
    @PostMapping("/send")
    public Result<ChatAnswerVO> send(@Valid @RequestBody ChatSendDTO dto) {
        return Result.success(chatService.send(SecurityUtils.getUserId(), dto));
    }

    /**
     * 业务分类与快捷提问(前端引导语)
     */
    @GetMapping("/categories")
    public Result<List<Map<String, String>>> categories() {
        List<Map<String, String>> list = InsuranceCategoryEnum.all().stream()
                .map(item -> Map.of(
                        "code", item.getCode(),
                        "name", item.getDesc(),
                        "sample", item.getSampleQuestion()))
                .toList();
        return Result.success(list);
    }
}
