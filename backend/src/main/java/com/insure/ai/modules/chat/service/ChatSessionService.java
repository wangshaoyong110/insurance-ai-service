package com.insure.ai.modules.chat.service;

import com.insure.ai.common.PageResult;
import com.insure.ai.common.enums.ChatMessageTypeEnum;
import com.insure.ai.common.enums.ChatRoleEnum;
import com.insure.ai.modules.chat.dto.ChatMessageVO;
import com.insure.ai.modules.chat.dto.ChatSessionVO;
import com.insure.ai.modules.chat.entity.ChatMessage;
import com.insure.ai.modules.chat.entity.ChatSession;

import java.util.List;
import java.util.Map;

/**
 * 会话与消息存取
 * <p>
 * 只负责会话增删改查、消息落库与消息读取,不含任何 AI 调用与人工接管状态流转。
 * 归属校验({@link #requireOwnSession})也集中在这里,避免各服务各写一份越权判断。
 * </p>
 *
 * @author insurance-ai
 */
public interface ChatSessionService {

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
     * 定位会话:sessionId 为空则按 titleSeed 新建,否则校验归属
     *
     * @param titleSeed 新建时用作会话标题的种子文本(通常是本轮提问内容)
     */
    ChatSession resolveSession(Long cusUserId, Long sessionId, String titleSeed);

    /**
     * 按 ID 取会话(坐席侧使用,鉴权在控制器层)
     */
    ChatSession requireSession(Long sessionId);

    /**
     * 校验会话归属,防止越权访问他人会话
     */
    ChatSession requireOwnSession(Long cusUserId, Long sessionId);

    /**
     * 追加一条消息并同步会话摘要(末条消息、时间、条数 +1)
     */
    ChatMessage appendMessage(Long sessionId, Long cusUserId, ChatRoleEnum role, ChatMessageTypeEnum type,
                              String content, String mediaUrl, String mediaName, Long mediaSize,
                              String senderName, String model, Integer tokens);

    /**
     * 只刷新会话的末条摘要与条数(不新增消息)
     * <p>用于"消息已由调用方自行落库,只需同步会话列表预览"的场景</p>
     */
    void touchSession(Long sessionId, String preview);

    /**
     * 落库一条纯文本消息(不更新会话摘要)
     * <p>供对话链路使用:摘要由 {@link #updateSessionSummary} 在问答结束后统一更新,避免条数重复累加</p>
     */
    ChatMessage saveMessage(Long sessionId, Long cusUserId, ChatRoleEnum role,
                            String content, String model, Integer tokens);

    /**
     * 更新会话摘要信息(末条消息、条数 +2、必要时用首轮提问补标题)
     */
    void updateSessionSummary(ChatSession session, String question, String answer);

    /**
     * 增量拉取会话消息(客户端轮询坐席回复)
     *
     * @param afterId 只返回 id 大于该值的消息,首次传 0
     */
    List<ChatMessageVO> listMessagesAfter(Long cusUserId, Long sessionId, Long afterId);

    /**
     * 坐席侧拉取会话消息(无需客户归属校验,由管理端鉴权)
     * <p>首次打开面板返回最近 200 条并翻回正序;之后用 afterId 做增量轮询</p>
     */
    List<ChatMessageVO> adminMessages(Long sessionId, Long afterId);

    /**
     * 后台统计:总消息数 / 今日会话数等
     */
    Map<String, Object> statistics();
}
