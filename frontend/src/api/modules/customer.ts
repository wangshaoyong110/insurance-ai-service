import { del, get, put } from '@/utils/request';
import type { CusUserQueryParams } from '@/types/api';
import type { CusUserInfo, PageResult } from '@/types';

/**
 * 后台客户管理接口(cus_user)
 */

/** 客户分页 */
export function getCustomerPageApi(params: CusUserQueryParams): Promise<PageResult<CusUserInfo>> {
  return get<PageResult<CusUserInfo>>('/admin/customer/page', params);
}

/** 客户详情 */
export function getCustomerDetailApi(id: number): Promise<CusUserInfo> {
  return get<CusUserInfo>(`/admin/customer/${id}`);
}

/** 启用 / 禁用客户 */
export function changeCustomerStatusApi(id: number, status: number): Promise<void> {
  return put<void>(`/admin/customer/${id}/status`, undefined, { params: { status } });
}

/** 重置客户密码 */
export function resetCustomerPasswordApi(id: number): Promise<void> {
  return put<void>(`/admin/customer/${id}/reset-password`);
}

/** 删除客户 */
export function deleteCustomerApi(id: number): Promise<void> {
  return del<void>(`/admin/customer/${id}`);
}
