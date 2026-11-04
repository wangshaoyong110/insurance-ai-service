<script setup lang="ts">
import { ref } from 'vue';
import { getOperLogPageApi } from '@/api';
import { useTable } from '@/hooks/useTable';
import type { FormField, OperLogItem, TableColumn } from '@/types';
import type { LogQueryParams } from '@/types/api';

/**
 * 操作日志:记录操作人、模块、接口、参数、耗时与异常
 */
const { loading, list, total, query, pagination, search, reset, handleCurrentChange, handleSizeChange } =
  useTable<OperLogItem, LogQueryParams>({
    api: getOperLogPageApi,
    defaultQuery: { keyword: '', status: undefined, startDate: '', endDate: '' },
  });

const searchFields: FormField[] = [
  { prop: 'keyword', label: '关键字', placeholder: '操作人 / 模块 / 操作' },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: '成功', value: 1 },
      { label: '失败', value: 0 },
    ],
  },
  { prop: 'startDate', label: '开始日期', type: 'date' },
  { prop: 'endDate', label: '结束日期', type: 'date' },
];

const columns: TableColumn<OperLogItem>[] = [
  { prop: 'operName', label: '操作人', width: 120 },
  { prop: 'module', label: '模块', width: 120 },
  { prop: 'operation', label: '操作', width: 140 },
  { prop: 'requestMethod', label: '方式', width: 80 },
  { prop: 'requestUri', label: '请求地址', minWidth: 180, showOverflowTooltip: true },
  { prop: 'ip', label: 'IP', width: 130 },
  { prop: 'browser', label: '浏览器', width: 100 },
  { prop: 'status', label: '状态', width: 80, slot: 'status' },
  { prop: 'costTime', label: '耗时(ms)', width: 100 },
  { prop: 'operTime', label: '操作时间', width: 180 },
  { label: '详情', width: 80, slot: 'action', fixed: 'right' },
];

/** 详情弹窗 */
const detailVisible = ref(false);
const detail = ref<OperLogItem | null>(null);

function openDetail(row: OperLogItem): void {
  detail.value = row;
  detailVisible.value = true;
}
</script>

<template>
  <div class="page-container">
    <BaseSearch
      :model="query"
      :fields="searchFields"
      :loading="loading"
      @search="search"
      @reset="reset"
    />

    <div class="card">
      <div class="card__header">
        <h3>操作日志</h3>
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
        <template #status="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>

        <template #action="{ row }">
          <el-button link type="primary" @click="openDetail(row)">查看</el-button>
        </template>
      </BaseTable>
    </div>

    <BaseDialog v-model="detailVisible" title="操作详情" width="720px" :show-footer="false">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="操作人">{{ detail.operName }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ detail.operTime }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ detail.module }}</el-descriptions-item>
        <el-descriptions-item label="操作">{{ detail.operation }}</el-descriptions-item>
        <el-descriptions-item label="请求方式">{{ detail.requestMethod }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detail.costTime }} ms</el-descriptions-item>
        <el-descriptions-item label="IP">{{ detail.ip }}</el-descriptions-item>
        <el-descriptions-item label="客户端">
          {{ detail.browser }} / {{ detail.os }}
        </el-descriptions-item>
        <el-descriptions-item label="请求地址" :span="2">
          {{ detail.requestUri }}
        </el-descriptions-item>
        <el-descriptions-item label="方法" :span="2">{{ detail.method }}</el-descriptions-item>
        <el-descriptions-item label="请求参数" :span="2">
          <pre class="detail-pre">{{ detail.params || '-' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.errorMsg" label="异常信息" :span="2">
          <pre class="detail-pre detail-pre--error">{{ detail.errorMsg }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </BaseDialog>
  </div>
</template>

<style scoped lang="scss">
.card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-md;

  h3 {
    font-size: $font-size-lg;
  }

  span {
    font-size: $font-size-sm;
  }
}

.detail-pre {
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: Consolas, Monaco, monospace;
  font-size: $font-size-xs;
  line-height: 1.7;
  margin: 0;
  @include scrollbar;

  &--error {
    color: $color-danger;
  }
}
</style>
