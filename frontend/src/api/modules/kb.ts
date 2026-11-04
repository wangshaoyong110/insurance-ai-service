import { del, get, post, put } from '@/utils/request';
import type { KbQueryParams, KbSaveParams } from '@/types/api';
import type { CategoryItem, KbItem, PageResult } from '@/types';

/**
 * 知识库管理接口
 */

/** 知识库分页 */
export function getKbPageApi(params: KbQueryParams): Promise<PageResult<KbItem>> {
  return get<PageResult<KbItem>>('/admin/kb/page', params);
}

/** 新增知识库 */
export function createKbApi(data: KbSaveParams): Promise<number> {
  return post<number>('/admin/kb', data);
}

/** 编辑知识库 */
export function updateKbApi(id: number, data: KbSaveParams): Promise<number> {
  return put<number>(`/admin/kb/${id}`, data);
}

/** 删除知识库 */
export function deleteKbApi(id: number): Promise<void> {
  return del<void>(`/admin/kb/${id}`);
}

/** 知识库分类字典 */
export function getKbCategoriesApi(): Promise<CategoryItem[]> {
  return get<CategoryItem[]>('/admin/kb/categories');
}
