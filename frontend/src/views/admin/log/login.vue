<script setup lang="ts">
import { getLoginLogPageApi } from '@/api';
import { useTable } from '@/hooks/useTable';
import type { FormField, LoginLogItem, TableColumn } from '@/types';
import type { LogQueryParams } from '@/types/api';

/**
 * 登录日志
 * 记录登录端(管理端 / 客户端)、账号、IP、浏览器、操作系统与登录结果
 */
const { loading, list, total, query, pagination, search, reset, handleCurrentChange, handleSizeChange } =
  useTable<LoginLogItem, LogQueryParams>({
    api: getLoginLogPageApi,
    defaultQuery: { keyword: '', status: undefined, loginType: undefined, startDate: '', endDate: '' },
  });

/** 查询条件 */
const searchFields: FormField[] = [
  { prop: 'keyword', label: '登录账号', placeholder: '请输入账号' },
  {
    prop: 'loginType',
    label: '登录端',
    type: 'select',
    options: [
      { label: '管理端', value: 'ADMIN' },
      { label: '客户端', value: 'CUSTOMER' },
    ],
  },
  {
    prop: 'status',
    label: '登录结果',
    type: 'select',
    options: [
      { label: '成功', value: 1 },
      { label: '失败', value: 0 },
    ],
  },
  { prop: 'startDate', label: '开始日期', type: 'date' },
  { prop: 'endDate', label: '结束日期', type: 'date' },
];

/** 表格列 */
const columns: TableColumn<LoginLogItem>[] = [
  { prop: 'username', label: '登录账号', minWidth: 130 },
  { prop: 'loginType', label: '登录端', width: 100, slot: 'loginType' },
  { prop: 'ip', label: 'IP 地址', width: 140 },
  { prop: 'browser', label: '浏览器', width: 120 },
  { prop: 'os', label: '操作系统', width: 110 },
  { prop: 'status', label: '结果', width: 90, slot: 'status' },
  { prop: 'msg', label: '提示信息', minWidth: 160, showOverflowTooltip: true },
  { prop: 'loginTime', label: '登录时间', width: 180 },
];
</script>

<template>
  <div class="page-container">
    <BaseSearch :model="query" :fields="searchFields" :loading="loading" @search="search" @reset="reset" />

    <div class="card">
      <div class="card__header">
        <div>
          <h3>登录日志</h3>
          <p class="text-secondary">管理端与客户端登录均会留痕,可按登录端筛选</p>
        </div>
        <span class="text-secondary">共 {{ total }} 条记录</span>
      </div>
      <BaseTable
        :data="list"
        :columns="columns"
        :loading="loading"
        :total="total"
        :page-num="pagination.pageNum"
        :page-size="pagination.pageSize"
        @update:page-num="handleCurrentChange"
        @update:page-size="handleSizeChange"
      >
        <template #loginType="{ row }">
          <el-tag :type="row.loginType === 'ADMIN' ? 'warning' : 'success'" size="small" effect="plain">
            {{ row.loginType === 'ADMIN' ? '管理端' : '客户端' }}
          </el-tag>
        </template>
        <template #status="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </BaseTable>
    </div>
  </div>
</template>

<style scoped lang="scss">
.card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: $spacing-md;

  h3 {
    font-size: $font-size-lg;
  }

  p {
    margin-top: 6px;
    font-size: 12px;
  }

  span {
    font-size: $font-size-sm;
  }
}
</style>
