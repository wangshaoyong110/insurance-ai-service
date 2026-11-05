package com.insure.ai.modules.chat.service;

import com.insure.ai.modules.chat.dto.ChatMessageSendDTO;
import com.insure.ai.modules.chat.dto.ChatMessageVO;

/**
 * 人工接管状态流转
 * <p>
 * 负责会话在「AI → 排队 → 人工 → AI」之间的迁移,以及人工在场时的双向消息收发。
 * 状态落点只有一个:{@code chat_session.service_mode}(配合 handler_id / handler_name)。
 * </p>
 *
 * @author insurance-ai
 */
public interface ChatTakeoverService {

    /**
     * 客户发送文字 / 图片 / 视频消息
     * <p>人工接入中不走大模型;AI 模式下前端仍走流式接口,本方法主要用于媒体消息</p>
     *
     * @param dto 消息内容,mediaUrl 必须是本站上传返回的地址
     */
    ChatMessageVO sendMessage(Long cusUserId, ChatMessageSendDTO dto);

    /**
     * 坐席发送消息
     * <p>必须已接单(会话处于人工接入中)才能发言,避免未接单就插话导致工单状态与会话状态不一致</p>
     */
    ChatMessageVO staffSend(Long sessionId, Long operatorId, String operatorName, ChatMessageSendDTO dto);

    /**
     * 客户提交工单后把会话标记为「排队等人工」,并绑定工单
     * <p>此阶段 AI 仍可回答,避免客户在排队时干等</p>
     */
    void bindTicket(Long cusUserId, Long sessionId, Long ticketId);

    /**
     * 坐席接单:接管会话,AI 静默,并插入一条系统提示
     */
    void takeover(Long sessionId, Long ticketId, Long operatorId, String operatorName);

    /**
     * 为客户新建一个会话并直接进入人工接管模式
     * <p>用于工单提交时没有关联会话的场景(如客户在「我的工单」页直接提交):
     * 接单时补建会话,否则坐席接单后没有任何联系客户的通道。
     * 新会话会出现在客户的「历史会话列表」里,系统提示「客服已接入」,客户可直接回复。</p>
     *
     * @return 新建的会话ID(调用方需回写到工单的 session_id)
     */
    Long createTakeoverSession(Long cusUserId, Long ticketId, String title, Long operatorId, String operatorName);

    /**
     * 结单:把坐席答复写入会话、插入结束提示,并把会话交回 AI
     */
    void finishTakeover(Long sessionId, String reply, String operatorName);

    /**
     * 工单取消:交回 AI
     */
    void cancelTakeover(Long sessionId, String notice);
}
