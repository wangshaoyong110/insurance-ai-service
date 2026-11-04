import { get } from '@/utils/request';
import type { ClaimQueryParams, PolicyQueryParams } from '@/types/api';
import type { ClaimDetail, ClaimItem, PageResult, PolicyItem } from '@/types';

/**
 * 后台保单 / 理赔查询接口
 * 只读:数据由核心业务系统同步,后台不做变更
 */

/** 保单分页 */
export function getPolicyPageApi(params: PolicyQueryParams): Promise<PageResult<PolicyItem>> {
  return get<PageResult<PolicyItem>>('/admin/policy/page', params);
}

/** 保单详情 */
export function getPolicyDetailApi(id: number): Promise<PolicyItem> {
  return get<PolicyItem>(`/admin/policy/${id}`);
}

/** 理赔分页 */
export function getClaimPageApi(params: ClaimQueryParams): Promise<PageResult<ClaimItem>> {
  return get<PageResult<ClaimItem>>('/admin/claim/page', params);
}

/** 理赔详情(含进度时间轴) */
export function getClaimDetailApi(id: number): Promise<ClaimDetail> {
  return get<ClaimDetail>(`/admin/claim/${id}`);
}
