import { del, get, post, put } from '@/utils/request';
import type { AdminUserQueryParams, AdminUserSaveParams } from '@/types/api';
import type { AdminUserInfo, PageResult, RoleItem } from '@/types';

/**
 * 后台管理员账号接口(sys_user)
 */

/** 管理员分页 */
export function getAdminUserPageApi(params: AdminUserQueryParams): Promise<PageResult<AdminUserInfo>> {
  return get<PageResult<AdminUserInfo>>('/admin/user/page', params);
}

/** 角色下拉选项 */
export function getRoleOptionsApi(): Promise<RoleItem[]> {
  return get<RoleItem[]>('/admin/user/roles');
}

/** 管理员详情 */
export function getAdminUserDetailApi(id: number): Promise<AdminUserInfo> {
  return get<AdminUserInfo>(`/admin/user/${id}`);
}

/** 新增管理员 */
export function createAdminUserApi(data: AdminUserSaveParams): Promise<number> {
  return post<number>('/admin/user', data);
}

/** 编辑管理员 */
export function updateAdminUserApi(id: number, data: AdminUserSaveParams): Promise<number> {
  return put<number>(`/admin/user/${id}`, data);
}

/** 删除管理员 */
export function deleteAdminUserApi(id: number): Promise<void> {
  return del<void>(`/admin/user/${id}`);
}

/** 重置密码 */
export function resetAdminPasswordApi(id: number): Promise<void> {
  return put<void>(`/admin/user/${id}/reset-password`);
}

/** 启用 / 禁用管理员 */
export function changeAdminStatusApi(id: number, status: number): Promise<void> {
  return put<void>(`/admin/user/${id}/status`, undefined, { params: { status } });
}
