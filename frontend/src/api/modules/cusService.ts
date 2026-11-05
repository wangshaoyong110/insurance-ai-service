import { cusGet, cusPost, cusPut } from '@/utils/cusRequest';
import type { FeedbackParams, TicketCreateParams } from '@/types/api';
import type { FeedbackItem, OptionItem, TicketItem } from '@/types';

/**
 * 客户端服务闭环接口:回答评价与转人工
 */

/* ---------------- 评价 ---------------- */

/** 提交对某条 AI 回复的评价(重复提交覆盖) */
export function submitFeedbackApi(data: FeedbackParams): Promise<void> {
  return cusPost<void>('/service/feedback', data);
}

/** 查询某条 AI 回复的已有评价 */
export function getFeedbackOfMessageApi(messageId: number): Promise<FeedbackItem | null> {
  return cusGet<FeedbackItem | null>(`/service/feedback/${messageId}`);
}

/** 差评原因选项 */
export function getFeedbackReasonsApi(): Promise<OptionItem[]> {
  return cusGet<OptionItem[]>('/service/feedback-reasons');
}

/* ---------------- 工单 ---------------- */

/** 转人工:创建工单 */
export function createTicketApi(data: TicketCreateParams): Promise<TicketItem> {
  return cusPost<TicketItem>('/service/tickets', data);
}

/** 我的工单列表 */
export function getMyTicketsApi(): Promise<TicketItem[]> {
  return cusGet<TicketItem[]>('/service/tickets');
}

/** 取消待处理工单 */
export function cancelTicketApi(id: number): Promise<void> {
  return cusPut<void>(`/service/tickets/${id}/cancel`);
}

/** 转人工原因选项 */
export function getTicketReasonsApi(): Promise<OptionItem[]> {
  return cusGet<OptionItem[]>('/service/ticket-reasons');
}
