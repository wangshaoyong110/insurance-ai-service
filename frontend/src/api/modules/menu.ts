import { del, get, post, put } from '@/utils/request';
import type { MenuSaveParams } from '@/types/api';
import type { MenuItem } from '@/types';

/**
 * 后台菜单管理接口
 */

/** 菜单树(管理端,包含停用菜单) */
export function getMenuTreeApi(onlyEnabled = false): Promise<MenuItem[]> {
  return get<MenuItem[]>('/admin/menu/tree', { onlyEnabled });
}

/** 新增菜单 */
export function createMenuApi(data: MenuSaveParams): Promise<number> {
  return post<number>('/admin/menu', data);
}

/** 编辑菜单 */
export function updateMenuApi(id: number, data: MenuSaveParams): Promise<number> {
  return put<number>(`/admin/menu/${id}`, data);
}

/** 删除菜单 */
export function deleteMenuApi(id: number): Promise<void> {
  return del<void>(`/admin/menu/${id}`);
}
