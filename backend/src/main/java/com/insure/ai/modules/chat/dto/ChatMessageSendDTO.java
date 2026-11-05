package com.insure.ai.modules.chat.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 会话消息发送参数(文字 / 图片 / 视频)
 * <p>
 * 文字消息 content 必填;媒体消息 mediaUrl 必填,content 作为图片说明可留空。
 * 媒体文件先走 /file/upload 上传,拿到 URL 后再发消息,避免把二进制塞进 JSON。
 * </p>
 *
 * @author insurance-ai
 */
@Data
public class ChatMessageSendDTO implements Serializable {

    /** 会话ID(坐席侧从路径取,可空) */
    private Long sessionId;

    /** 文本内容;媒体消息可留空 */
    @Size(max = 1000, message = "消息内容不能超过 1000 字")
    private String content;

    /** 消息类型:text / image / video */
    private String msgType = "text";

    /** 媒体地址(图片/视频) */
    @Size(max = 500, message = "媒体地址过长")
    private String mediaUrl;

    /** 原始文件名 */
    @Size(max = 200, message = "文件名过长")
    private String mediaName;

    /** 文件大小(字节) */
    private Long mediaSize;
}
