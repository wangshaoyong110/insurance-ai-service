import { cusDel, cusGet, cusPost, cusPut, cusUpload } from '@/utils/cusRequest';
import { postSse, type SseHandlers } from '@/utils/sse';
import type { ChatMessageSendParams, ChatSendParams } from '@/types/api';
import type {
  CategoryItem,
  ChatAnswer,
  ChatMessageItem,
  ChatSessionItem,
  PageResult,
  UploadResult,
} from '@/types';

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

/**
 * 会话消息列表(历史查询)
 * 分页从最新往回翻，records 内部已按时间正序，可直接渲染
 */
export function getMessagesApi(
  id: number,
  pageNum = 1,
  pageSize = 50,
): Promise<PageResult<ChatMessageItem>> {
  return cusGet<PageResult<ChatMessageItem>>(`/chat/sessions/${id}/messages`, {
    params: { pageNum, pageSize },
  });
}

/**
 * 发送消息
 * 说明:AI 推理耗时较长,单独放宽超时时间到 60s
 */
export function sendMessageApi(data: ChatSendParams): Promise<ChatAnswer> {
  return cusPost<ChatAnswer>('/chat/send', data, { timeout: 60000, cancelDuplicate: false });
}

/**
 * 流式发送消息(SSE)
 * <p>首字延迟从"整段生成完"降到 1 秒内；需传 AbortSignal 以便离开页面时断开</p>
 */
export function sendMessageStreamApi(
  data: ChatSendParams,
  handlers: SseHandlers,
  signal?: AbortSignal,
): Promise<void> {
  return postSse('/chat/send/stream', data, handlers, signal);
}

/** 业务分类(快捷提问) */
export function getCategoriesApi(): Promise<CategoryItem[]> {
  return cusGet<CategoryItem[]>('/chat/categories');
}

/** 会话详情(含服务模式,用于判断是否已转人工) */
export function getSessionDetailApi(id: number): Promise<ChatSessionItem> {
  return cusGet<ChatSessionItem>(`/chat/sessions/${id}`);
}

/** 增量拉取消息(轮询坐席回复) */
export function getMessagesAfterApi(id: number, afterId = 0): Promise<ChatMessageItem[]> {
  return cusGet<ChatMessageItem[]>(`/chat/sessions/${id}/messages/after`, { params: { afterId } });
}

/** 发送消息(文字 / 图片 / 视频) */
export function sendChatMessageApi(data: ChatMessageSendParams): Promise<ChatMessageItem> {
  return cusPost<ChatMessageItem>('/chat/messages', data, { loading: false });
}

/**
 * 上传聊天附件(图片 / 视频)
 * 视频可能几十 MB,超时时间放宽到 2 分钟
 */
export function uploadChatFileApi(file: File): Promise<UploadResult> {
  return cusUpload<UploadResult>('/file/upload', file, { bizType: 'chat' }, { timeout: 120000 });
}
