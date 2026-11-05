package com.insure.ai.modules.chat.service;

import com.insure.ai.common.PageResult;
import com.insure.ai.modules.chat.dto.ChatAnswerVO;
import com.insure.ai.modules.chat.dto.ChatMessageSendDTO;
import com.insure.ai.modules.chat.dto.ChatMessageVO;
import com.insure.ai.modules.chat.dto.ChatSendDTO;
import com.insure.ai.modules.chat.dto.ChatSessionVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import java.util.Map;

/**
 * 智能客服会话服务
 * <p>
 * 对话链路(Spring AI Alibaba / Spring AI ChatClient):
 * <ol>
 *     <li>知识库关键词召回,拼进 system 提示词</li>
 *     <li>按数据库中的默认模型配置构建 {@link ChatClient}(配置可在后台改,无需重启)</li>
 *     <li>挂载 {@link InsuranceQueryTools},模型可自行决定是否调用保单/理赔查询</li>
 *     <li>回复与 token 消耗落库</li>
 * </ol>
 *
 * @author insurance-ai
 */
public interface ChatService {

    /**
     * 查询当前用户的历史会话(按最后消息时间倒序)
     */
    List<ChatSessionVO> listSessions(Long cusUserId);

    /**
     * 查询某个会话的消息(从最新往回翻，返回结果内部按时间正序，便于直接渲染)
     * <p>默认每页 50 条：单个会话攒到几百条后一次性全量下发会明显拖慢首屏</p>
     */
    PageResult<ChatMessageVO> listMessages(Long cusUserId, Long sessionId, Long pageNum, Long pageSize);

    /**
     * 新建会话(空会话,用户可先建后问)
     */
    ChatSessionVO createSession(Long cusUserId, String title);

    /**
     * 会话详情(含服务模式,前端据此判断是否需要轮询坐席消息)
     */
    ChatSessionVO sessionDetail(Long cusUserId, Long sessionId);

    /**
     * 删除会话(逻辑删除,同时删除会话下的消息)
     */
    void deleteSession(Long cusUserId, Long sessionId);

    /**
     * 重命名会话
     */
    void renameSession(Long cusUserId, Long sessionId, String title);

    /**
     * 发送消息并获取 AI 回复
     * <p>注意:该方法不整体加事务,避免调用大模型期间长时间占用数据库连接</p>
     */
    ChatAnswerVO send(Long cusUserId, ChatSendDTO dto);

    /* ================= 人工接管(工单与会话打通) ================= */

    /**
     * 客户发送文字 / 图片 / 视频消息
     * <p>人工接入中不走大模型;AI 模式下前端仍走流式接口,本方法主要用于媒体消息</p>
     *
     * @param dto 消息内容,mediaUrl 必须是本站上传返回的地址
     */
    ChatMessageVO sendMessage(Long cusUserId, ChatMessageSendDTO dto);

    /**
     * 增量拉取会话消息(客户端轮询坐席回复)
     *
     * @param afterId 只返回 id 大于该值的消息,首次传 0
     */
    List<ChatMessageVO> listMessagesAfter(Long cusUserId, Long sessionId, Long afterId);

    /**
     * 坐席侧拉取会话消息(无需客户归属校验,由管理端鉴权)
     */
    List<ChatMessageVO> adminMessages(Long sessionId, Long afterId);

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

    /**
     * 流式发送消息(SSE)
     * <p>
     * 与 {@link #send} 的差异只在传输形态:校验、建会话、用户消息落库仍在请求线程内同步完成
     * (失败可正常返回 JSON 错误码),模型生成阶段放进线程池逐段推送,结束后统一落库。
     * </p>
     * <p>事件协议:{@code meta}(会话与参考来源) → {@code delta}(增量文本,可多次) → {@code done} 或 {@code error}。</p>
     */
    SseEmitter sendStream(Long cusUserId, ChatSendDTO dto);

    /**
     * 后台统计:总消息数 / 今日会话数等
     */
    Map<String, Object> statistics();
}
