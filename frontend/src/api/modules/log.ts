import { get } from '@/utils/request';
import type { LogQueryParams } from '@/types/api';
import type { LoginLogItem, OperLogItem, PageResult } from '@/types';

/**
 * 日志查询接口
 */

/** 登录日志分页(可按登录端筛选) */
export function getLoginLogPageApi(params: LogQueryParams): Promise<PageResult<LoginLogItem>> {
  return get<PageResult<LoginLogItem>>('/admin/log/login', params);
}

/** 操作日志分页 */
export function getOperLogPageApi(params: LogQueryParams): Promise<PageResult<OperLogItem>> {
  return get<PageResult<OperLogItem>>('/admin/log/oper', params);
}
