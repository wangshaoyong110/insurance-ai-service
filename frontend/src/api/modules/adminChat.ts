import { get, post } from '@/utils/request';
import type { ChatMessageSendParams } from '@/types/api';
import type { ChatMessageItem } from '@/types';

/**
 * 坐席会话接口(管理端)
 * <p>坐席接单后在原会话里与客户沟通,与客户端共用同一份消息数据</p>
 */

/** 会话消息(首次拉最近 200 条,之后用 afterId 增量) */
export function getAdminSessionMessagesApi(
  sessionId: number,
  afterId = 0,
): Promise<ChatMessageItem[]> {
  return get<ChatMessageItem[]>(`/admin/chat/sessions/${sessionId}/messages`, { afterId });
}

/** 坐席发送消息 */
export function sendStaffMessageApi(
  sessionId: number,
  data: ChatMessageSendParams,
): Promise<ChatMessageItem> {
  return post<ChatMessageItem>(`/admin/chat/sessions/${sessionId}/send`, data);
}
