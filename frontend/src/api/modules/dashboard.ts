import { get } from '@/utils/request';
import type { DashboardStatistics } from '@/types';

/**
 * 后台概览统计接口
 */

/** 首页统计数据(管理端账号 / 客户端客户分开统计) */
export function getStatisticsApi(): Promise<DashboardStatistics> {
  return get<DashboardStatistics>('/admin/dashboard/statistics');
}
