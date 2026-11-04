package com.insure.ai.modules.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 发送消息参数
 *
 * @author insurance-ai
 */
@Data
public class ChatSendDTO implements Serializable {

    /** 会话ID,为空表示新建会话 */
    private Long sessionId;

    /** 用户问题 */
    @NotBlank(message = "请输入您的问题")
    @Size(max = 1000, message = "问题内容不能超过 1000 字")
    private String content;
}
