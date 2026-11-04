import { del, get, post, put } from '@/utils/request';
import type { RoleSaveParams } from '@/types/api';
import type { RoleDetail, RoleItem } from '@/types';

/**
 * 后台角色管理接口
 */

/** 角色列表 */
export function getRoleListApi(): Promise<RoleItem[]> {
  return get<RoleItem[]>('/admin/role/list');
}

/** 角色下拉选项 */
export function getRoleSelectApi(): Promise<RoleItem[]> {
  return get<RoleItem[]>('/admin/role/options');
}

/** 角色详情(含已授权菜单) */
export function getRoleDetailApi(id: number): Promise<RoleDetail> {
  return get<RoleDetail>(`/admin/role/${id}`);
}

/** 新增角色 */
export function createRoleApi(data: RoleSaveParams): Promise<number> {
  return post<number>('/admin/role', data);
}

/** 编辑角色 */
export function updateRoleApi(id: number, data: RoleSaveParams): Promise<number> {
  return put<number>(`/admin/role/${id}`, data);
}

/** 删除角色 */
export function deleteRoleApi(id: number): Promise<void> {
  return del<void>(`/admin/role/${id}`);
}
