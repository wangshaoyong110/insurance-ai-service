import { cusDel, cusGet, cusPost, cusPut } from '@/utils/cusRequest';
import type { ChatSendParams } from '@/types/api';
import type { CategoryItem, ChatAnswer, ChatMessageItem, ChatSessionItem } from '@/types';

/**
 * 客户端智能客服接口
 */

/** 历史会话列表 */
export function getSessionsApi(): Promise<ChatSessionItem[]> {
  return cusGet<ChatSessionItem[]>('/chat/sessions');
}

/** 新建空会话 */
export function createSessionApi(title?: string): Promise<ChatSessionItem> {
  return cusPost<ChatSessionItem>('/chat/sessions', undefined, { params: { title } });
}

/** 删除会话 */
export function deleteSessionApi(id: number): Promise<void> {
  return cusDel<void>(`/chat/sessions/${id}`);
}

/** 重命名会话 */
export function renameSessionApi(id: number, title: string): Promise<void> {
  return cusPut<void>(`/chat/sessions/${id}`, undefined, { params: { title } });
}

/** 会话消息列表(历史查询) */
export function getMessagesApi(id: number): Promise<ChatMessageItem[]> {
  return cusGet<ChatMessageItem[]>(`/chat/sessions/${id}/messages`);
}

/**
 * 发送消息
 * 说明:AI 推理耗时较长,单独放宽超时时间到 60s
 */
export function sendMessageApi(data: ChatSendParams): Promise<ChatAnswer> {
  return cusPost<ChatAnswer>('/chat/send', data, { timeout: 60000, cancelDuplicate: false });
}

/** 业务分类(快捷提问) */
export function getCategoriesApi(): Promise<CategoryItem[]> {
  return cusGet<CategoryItem[]>('/chat/categories');
}
