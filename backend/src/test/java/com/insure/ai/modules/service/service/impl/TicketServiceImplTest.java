package com.insure.ai.modules.service.service.impl;

import com.insure.ai.modules.chat.service.ChatService;
import com.insure.ai.modules.service.dto.TicketHandleDTO;
import com.insure.ai.modules.service.entity.ServiceTicket;
import com.insure.ai.modules.service.mapper.ServiceTicketMapper;
import com.insure.ai.modules.system.mapper.CusUserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 工单与会话联动单测
 * <p>
 * 工单状态和会话状态必须同步:接单不收口会话,坐席和客户就各说各话;
 * 结单不交回 AI,客户后续提问会永远石沉大海。这里逐条锁死。
 * </p>
 *
 * @author shaoyong.wang
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("工单与会话联动")
class TicketServiceImplTest {

    private static final Long TICKET_ID = 1L;
    private static final Long SESSION_ID = 8L;
    private static final Long CUSTOMER_ID = 2L;
    private static final Long OPERATOR_ID = 9L;
    private static final String OPERATOR = "客服小王";

    @Mock
    private ServiceTicketMapper ticketMapper;
    @Mock
    private CusUserMapper cusUserMapper;
    @Mock
    private ChatService chatService;

    @InjectMocks
    private TicketServiceImpl ticketService;

    private ServiceTicket ticket(int status, Long sessionId) {
        ServiceTicket ticket = new ServiceTicket();
        ticket.setId(TICKET_ID);
        ticket.setTicketNo("TK202611050001");
        ticket.setCusUserId(CUSTOMER_ID);
        ticket.setSessionId(sessionId);
        ticket.setStatus(status);
        ticket.setPriority(1);
        return ticket;
    }

    @Test
    @DisplayName("接单时接管会话,AI 静默")
    void acceptShouldTakeoverSession() {
        when(ticketMapper.selectById(TICKET_ID)).thenReturn(ticket(0, SESSION_ID));
        TicketHandleDTO dto = new TicketHandleDTO();
        dto.setStatus(1);

        ticketService.handle(TICKET_ID, dto, OPERATOR_ID, OPERATOR);

        verify(chatService).takeover(SESSION_ID, TICKET_ID, OPERATOR_ID, OPERATOR);
    }

    @Test
    @DisplayName("结单时把答复写进会话并交回 AI")
    void finishShouldWriteReplyAndReleaseSession() {
        when(ticketMapper.selectById(TICKET_ID)).thenReturn(ticket(1, SESSION_ID));
        TicketHandleDTO dto = new TicketHandleDTO();
        dto.setStatus(2);
        dto.setReply("已核实材料,预计 3 个工作日出结论");

        ticketService.handle(TICKET_ID, dto, OPERATOR_ID, OPERATOR);

        verify(chatService).finishTakeover(SESSION_ID, "已核实材料,预计 3 个工作日出结论", OPERATOR);
    }

    @Test
    @DisplayName("取消工单时交回 AI")
    void cancelShouldReleaseSession() {
        when(ticketMapper.selectById(TICKET_ID)).thenReturn(ticket(1, SESSION_ID));
        TicketHandleDTO dto = new TicketHandleDTO();
        dto.setStatus(3);

        ticketService.handle(TICKET_ID, dto, OPERATOR_ID, OPERATOR);

        verify(chatService).cancelTakeover(org.mockito.ArgumentMatchers.eq(SESSION_ID),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("未关联会话的工单接单时补建会话并回写 sessionId")
    void ticketWithoutSessionShouldCreateOneOnAccept() {
        ServiceTicket noSession = ticket(0, null);
        when(ticketMapper.selectById(TICKET_ID)).thenReturn(noSession);
        when(chatService.createTakeoverSession(CUSTOMER_ID, TICKET_ID, noSession.getQuestion(),
                OPERATOR_ID, OPERATOR)).thenReturn(SESSION_ID);
        TicketHandleDTO dto = new TicketHandleDTO();
        dto.setStatus(1);

        ticketService.handle(TICKET_ID, dto, OPERATOR_ID, OPERATOR);

        verify(chatService).createTakeoverSession(CUSTOMER_ID, TICKET_ID, noSession.getQuestion(),
                OPERATOR_ID, OPERATOR);
        verify(chatService, never()).takeover(any(), any(), any(), any());
    }

    @Test
    @DisplayName("结单/取消时未关联会话的工单不触碰会话")
    void closedTicketWithoutSessionShouldNotTouchChat() {
        when(ticketMapper.selectById(TICKET_ID)).thenReturn(ticket(1, null));
        TicketHandleDTO dto = new TicketHandleDTO();
        dto.setStatus(3);

        ticketService.handle(TICKET_ID, dto, OPERATOR_ID, OPERATOR);

        verifyNoInteractions(chatService);
    }

    @Test
    @DisplayName("结单未填回复时拒绝,避免客户只看到已完成却不知道结果")
    void finishWithoutReplyShouldFail() {
        when(ticketMapper.selectById(TICKET_ID)).thenReturn(ticket(1, SESSION_ID));
        TicketHandleDTO dto = new TicketHandleDTO();
        dto.setStatus(2);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> ticketService.handle(TICKET_ID, dto, OPERATOR_ID, OPERATOR)))
                .isNotNull();
        verify(chatService, never()).finishTakeover(any(), any(), any());
    }
}
