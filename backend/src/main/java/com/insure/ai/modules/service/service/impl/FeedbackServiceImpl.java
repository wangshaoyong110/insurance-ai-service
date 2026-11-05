package com.insure.ai.modules.service.service.impl;

import com.insure.ai.modules.service.service.FeedbackService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.PageResult;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.ChatRoleEnum;
import com.insure.ai.common.enums.FeedbackReasonEnum;
import com.insure.ai.modules.chat.entity.ChatMessage;
import com.insure.ai.modules.chat.mapper.ChatMessageMapper;
import com.insure.ai.modules.service.dto.FeedbackDTO;
import com.insure.ai.modules.service.dto.FeedbackQuery;
import com.insure.ai.modules.service.entity.ChatFeedback;
import com.insure.ai.modules.service.mapper.ChatFeedbackMapper;
import com.insure.ai.modules.system.entity.CusUser;
import com.insure.ai.modules.system.mapper.CusUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AI 回答评价服务
 * <p>
 * 为什么需要它:没有评价数据时,"回答质量"只能靠感觉判断——模型换了、提示词改了、
 * 知识库补了,到底有没有变好无从验证。落库后至少能得到好评率与差评归因两个可跟踪指标。
 * </p>
 *
 * @author insurance-ai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    /** 管理端列表里答案摘要的最大长度 */
    private static final int PREVIEW_LENGTH = 120;

    private final ChatFeedbackMapper feedbackMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final CusUserMapper cusUserMapper;

    /**
     * 提交评价(同一消息重复提交做覆盖更新)
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void submit(Long cusUserId, FeedbackDTO dto) {
        ChatMessage message = chatMessageMapper.selectById(dto.getMessageId());
        // 三重校验:消息存在、属于本人、且是 AI 回复。缺任意一条都可能被用来给他人会话刷评价
        if (message == null
                || !Objects.equals(message.getCusUserId(), cusUserId)
                || !ChatRoleEnum.ASSISTANT.getCode().equals(message.getRole())) {
            throw new BusinessException(ResultCode.FEEDBACK_MESSAGE_INVALID);
        }

        ChatFeedback exists = feedbackMapper.selectOne(Wrappers.<ChatFeedback>lambdaQuery()
                .eq(ChatFeedback::getMessageId, dto.getMessageId())
                .eq(ChatFeedback::getCusUserId, cusUserId)
                .last("LIMIT 1"));

        ChatFeedback entity = exists == null ? new ChatFeedback() : exists;
        entity.setMessageId(dto.getMessageId());
        entity.setSessionId(message.getSessionId());
        entity.setCusUserId(cusUserId);
        entity.setRating(dto.getRating());
        // 点赞不追问原因;点踩必须有归因,否则这条数据对改进毫无用处
        entity.setReason(dto.getRating() == 1 ? null : FeedbackReasonEnum.of(dto.getReason()).getCode());
        entity.setContent(dto.getContent());
        if (exists == null) {
            feedbackMapper.insert(entity);
        } else {
            feedbackMapper.updateById(entity);
        }
        log.info("[评价] 提交 messageId={} cusUserId={} rating={} reason={}",
                dto.getMessageId(), cusUserId, dto.getRating(), entity.getReason());
    }

    /**
     * 查询某条消息的评价(前端刷新页面后回显)
     */
    @Override
    public ChatFeedback ofMessage(Long cusUserId, Long messageId) {
        return feedbackMapper.selectOne(Wrappers.<ChatFeedback>lambdaQuery()
                .eq(ChatFeedback::getMessageId, messageId)
                .eq(ChatFeedback::getCusUserId, cusUserId)
                .last("LIMIT 1"));
    }

    /**
     * 管理端评价分页(差评优先展示,便于运营先看问题)
     */
    @Override
    public PageResult<ChatFeedback> page(FeedbackQuery query) {
        List<Long> customerIds = null;
        if (StringUtils.hasText(query.getCustomer())) {
            customerIds = matchCustomerIds(query.getCustomer());
            if (customerIds.isEmpty()) {
                return PageResult.empty(query.getPageNum(), query.getPageSize());
            }
        }
        Page<ChatFeedback> page = new Page<>(query.getPageNum(), query.getPageSize());
        feedbackMapper.selectPage(page, Wrappers.<ChatFeedback>lambdaQuery()
                .eq(query.getRating() != null, ChatFeedback::getRating, query.getRating())
                .eq(StringUtils.hasText(query.getReason()), ChatFeedback::getReason, query.getReason())
                .in(customerIds != null, ChatFeedback::getCusUserId, customerIds)
                .ge(StringUtils.hasText(query.getStartDate()), ChatFeedback::getCreateTime,
                        StringUtils.hasText(query.getStartDate())
                                ? LocalDate.parse(query.getStartDate()).atStartOfDay() : null)
                .le(StringUtils.hasText(query.getEndDate()), ChatFeedback::getCreateTime,
                        StringUtils.hasText(query.getEndDate())
                                ? LocalDate.parse(query.getEndDate()).atTime(23, 59, 59) : null)
                .orderByAsc(ChatFeedback::getRating)
                .orderByDesc(ChatFeedback::getId));
        PageResult<ChatFeedback> result = PageResult.of(page, item -> item);
        fillExtra(result.getRecords());
        return result;
    }

    /**
     * 评价统计:好评率、差评数、差评原因分布
     */
    @Override
    public Map<String, Object> statistics() {
        long total = feedbackMapper.selectCount(null);
        long good = feedbackMapper.selectCount(Wrappers.<ChatFeedback>lambdaQuery()
                .eq(ChatFeedback::getRating, 1));
        long bad = total - good;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("good", good);
        data.put("bad", bad);
        // 没有评价时返回 null 而不是 0,前端才能区分"好评率 0%"与"还没有数据"
        data.put("goodRate", total == 0 ? null : Math.round(good * 100.0 / total));

        Map<String, Long> reasonDist = new LinkedHashMap<>();
        feedbackMapper.selectList(Wrappers.<ChatFeedback>lambdaQuery()
                        .eq(ChatFeedback::getRating, 0)
                        .isNotNull(ChatFeedback::getReason))
                .forEach(item -> reasonDist.merge(item.getReason(), 1L, Long::sum));
        data.put("reasonDist", reasonDist);
        return data;
    }

    // ==================================================================
    // 内部方法
    // ==================================================================

    /**
     * 回填客户信息与答案摘要
     */
    private void fillExtra(List<ChatFeedback> list) {
        if (list.isEmpty()) {
            return;
        }
        Map<Long, CusUser> users = loadUsers(list.stream()
                .map(ChatFeedback::getCusUserId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, ChatMessage> messages = loadMessages(list.stream()
                .map(ChatFeedback::getMessageId).filter(Objects::nonNull).collect(Collectors.toSet()));
        for (ChatFeedback item : list) {
            CusUser user = users.get(item.getCusUserId());
            if (user != null) {
                item.setCustomerName(StringUtils.hasText(user.getRealName())
                        ? user.getRealName() : user.getNickname());
                item.setCustomerPhone(user.getPhone());
            }
            ChatMessage message = messages.get(item.getMessageId());
            if (message != null && StringUtils.hasText(message.getContent())) {
                String content = message.getContent();
                item.setAnswerPreview(content.length() > PREVIEW_LENGTH
                        ? content.substring(0, PREVIEW_LENGTH) + "..." : content);
            }
            item.setFeedbackTime(item.getCreateTime());
        }
    }

    private Map<Long, CusUser> loadUsers(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return cusUserMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(CusUser::getId, user -> user, (a, b) -> a));
    }

    private Map<Long, ChatMessage> loadMessages(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return chatMessageMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(ChatMessage::getId, message -> message, (a, b) -> a));
    }

    private List<Long> matchCustomerIds(String keyword) {
        return cusUserMapper.selectList(Wrappers.<CusUser>lambdaQuery()
                        .and(wrapper -> wrapper
                                .like(CusUser::getNickname, keyword)
                                .or().like(CusUser::getRealName, keyword)
                                .or().like(CusUser::getPhone, keyword)))
                .stream()
                .map(CusUser::getId)
                .toList();
    }
}
