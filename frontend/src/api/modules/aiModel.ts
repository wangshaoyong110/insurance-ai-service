import { del, get, post, put } from '@/utils/request';
import type { AiModelSaveParams, AiModelTestParams } from '@/types/api';
import type { AiModelItem, AiModelTestResult } from '@/types';

/**
 * 后台大模型配置接口
 * API Key 由后端 AES-256-GCM 加密入库,列表只返回掩码
 */

/** 配置列表 */
export function getAiModelListApi(): Promise<AiModelItem[]> {
  return get<AiModelItem[]>('/admin/model/list');
}

/** 新增配置 */
export function createAiModelApi(data: AiModelSaveParams): Promise<number> {
  return post<number>('/admin/model', data);
}

/** 编辑配置 */
export function updateAiModelApi(id: number, data: AiModelSaveParams): Promise<number> {
  return put<number>(`/admin/model/${id}`, data);
}

/** 删除配置 */
export function deleteAiModelApi(id: number): Promise<void> {
  return del<void>(`/admin/model/${id}`);
}

/** 启用 / 停用 */
export function changeAiModelStatusApi(id: number, status: number): Promise<void> {
  return put<void>(`/admin/model/${id}/status`, undefined, { params: { status } });
}

/** 设为默认配置 */
export function setDefaultAiModelApi(id: number): Promise<void> {
  return put<void>(`/admin/model/${id}/default`);
}

/** 连通性测试 */
export function testAiModelApi(data: AiModelTestParams): Promise<AiModelTestResult> {
  return post<AiModelTestResult>('/admin/model/test', data);
}
