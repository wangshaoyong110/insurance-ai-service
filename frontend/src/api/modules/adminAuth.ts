import { get, post, put } from '@/utils/request';
import type { AdminLoginResult, ChangePasswordParams, LoginParams } from '@/types/api';
import type { AdminUserInfo, MenuItem } from '@/types';

/**
 * 管理端认证接口(业务操作人员)
 * 与客户端认证接口 /auth/* 完全独立
 */

/** 管理端登录 */
export function adminLoginApi(data: LoginParams): Promise<AdminLoginResult> {
  return post<AdminLoginResult>('/admin/auth/login', data);
}

/** 管理端退出登录 */
export function adminLogoutApi(): Promise<void> {
  return post<void>('/admin/auth/logout');
}

/** 当前管理员信息 */
export function getAdminInfoApi(): Promise<AdminUserInfo> {
  return get<AdminUserInfo>('/admin/auth/me');
}

/** 当前管理员的菜单树(菜单驱动路由) */
export function getAdminMenusApi(): Promise<MenuItem[]> {
  return get<MenuItem[]>('/admin/auth/menus');
}

/** 修改密码 */
export function changeAdminPasswordApi(data: ChangePasswordParams): Promise<void> {
  return put<void>('/admin/auth/password', data);
}
