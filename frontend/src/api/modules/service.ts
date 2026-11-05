import { get, put } from '@/utils/request';
import type { FeedbackQueryParams, TicketHandleParams, TicketQueryParams } from '@/types/api';
import type { FeedbackItem, FeedbackStatistics, PageResult, TicketItem, TicketStatistics } from '@/types';

/**
 * 服务闭环接口(管理端):工单处理与评价分析
 */

/* ---------------- 工单 ---------------- */

/** 工单分页 */
export function getTicketPageApi(params: TicketQueryParams): Promise<PageResult<TicketItem>> {
  return get<PageResult<TicketItem>>('/admin/ticket/page', params);
}

/** 工单详情 */
export function getTicketDetailApi(id: number): Promise<TicketItem> {
  return get<TicketItem>(`/admin/ticket/${id}`);
}

/** 工单流转:接单 / 结单 / 取消 */
export function handleTicketApi(id: number, data: TicketHandleParams): Promise<TicketItem> {
  return put<TicketItem>(`/admin/ticket/${id}/handle`, data);
}

/** 工单统计 */
export function getTicketStatisticsApi(): Promise<TicketStatistics> {
  return get<TicketStatistics>('/admin/ticket/statistics');
}

/* ---------------- 评价 ---------------- */

/** 评价分页(差评优先) */
export function getFeedbackPageApi(params: FeedbackQueryParams): Promise<PageResult<FeedbackItem>> {
  return get<PageResult<FeedbackItem>>('/admin/feedback/page', params);
}

/** 评价统计 */
export function getFeedbackStatisticsApi(): Promise<FeedbackStatistics> {
  return get<FeedbackStatistics>('/admin/feedback/statistics');
}
