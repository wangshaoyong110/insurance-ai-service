package com.insure.ai.modules.chat.service;

import com.insure.ai.common.PageResult;
import com.insure.ai.modules.chat.dto.ChatAnswerVO;
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
