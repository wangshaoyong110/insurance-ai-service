package com.insure.ai.common.enums;

import lombok.Getter;

/**
 * 会话消息类型
 * <p>人工客服接入后需要收发图片与视频(比如客户拍理赔材料、坐席发操作截图),
 * 用 msg_type 区分渲染方式,content 仍保留文字/说明,便于搜索与摘要。</p>
 *
 * @author insurance-ai
 */
@Getter
public enum ChatMessageTypeEnum {

    /** 纯文本 */
    TEXT("text", "文本"),
    /** 图片 */
    IMAGE("image", "图片"),
    /** 视频 */
    VIDEO("video", "视频"),
    /** 系统提示(客服已接入/服务已结束),居中灰条展示 */
    SYSTEM("system", "系统提示");

    private final String code;
    private final String desc;

    ChatMessageTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按 code 解析,未知值按文本处理
     */
    public static ChatMessageTypeEnum of(String code) {
        if (code != null) {
            for (ChatMessageTypeEnum item : values()) {
                if (item.code.equalsIgnoreCase(code)) {
                    return item;
                }
            }
        }
        return TEXT;
    }

    /**
     * 是否为媒体消息
     */
    public boolean isMedia() {
        return this == IMAGE || this == VIDEO;
    }
}
