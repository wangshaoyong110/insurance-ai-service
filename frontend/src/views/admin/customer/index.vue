<script setup lang="ts">
import { ref } from 'vue';
import {
  changeCustomerStatusApi,
  deleteCustomerApi,
  getCustomerPageApi,
  resetCustomerPasswordApi,
} from '@/api';
import { useTable } from '@/hooks/useTable';
import { maskPhone } from '@/utils';
import type { CusUserInfo, FormField, TableColumn } from '@/types';
import type { CusUserQueryParams } from '@/types/api';

/**
 * 客户列表(cus_user)
 * 客户来自手机端自主注册,后台仅做状态管理与密码重置,不直接新增
 */
const { loading, list, total, query, pagination, loadData, search, reset, handleCurrentChange, handleSizeChange } =
  useTable<CusUserInfo, CusUserQueryParams>({
    api: getCustomerPageApi,
    defaultQuery: { keyword: '', status: undefined },
  });

/** 查询条件 */
const searchFields: FormField[] = [
  { prop: 'keyword', label: '关键字', placeholder: '昵称 / 姓名 / 手机号' },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: '启用', value: 1 },
      { label: '禁用', value: 0 },
    ],
  },
];

/** 表格列 */
const columns: TableColumn<CusUserInfo>[] = [
  { prop: 'id', label: 'ID', width: 60 },
  { prop: 'nickname', label: '昵称', minWidth: 100 },
  { prop: 'realName', label: '姓名', minWidth: 90, formatter: (row) => row.realName || '-' },
  {
    prop: 'phone',
    label: '手机号',
    width: 120,
    formatter: (row: CusUserInfo) => maskPhone(row.phone),
  },
  { prop: 'sessionCount', label: '咨询次数', width: 90, align: 'center' },
  { prop: 'registerSource', label: '来源', width: 80, slot: 'source' },
  { prop: 'status', label: '状态', width: 80, slot: 'status' },
  { prop: 'createTime', label: '注册时间', width: 150 },
  { prop: 'lastLoginTime', label: '最后登录', width: 150 },
  { label: '操作', width: 190, slot: 'action', fixed: 'right' },
];

/** 详情弹窗 */
const detailVisible = ref(false);
const detail = ref<CusUserInfo | null>(null);

/**
 * 查看客户详情
 */
function openDetail(row: CusUserInfo): void {
  detail.value = row;
  detailVisible.value = true;
}

/**
 * 启用 / 禁用客户
 */
async function handleStatusChange(row: CusUserInfo): Promise<void> {
  const nextStatus = row.status === 1 ? 0 : 1;
  await changeCustomerStatusApi(row.id, nextStatus);
  ElMessage.success(nextStatus === 1 ? '已启用该客户账号' : '已禁用该客户账号');
  await loadData();
}

/**
 * 重置客户密码
 */
async function handleResetPassword(row: CusUserInfo): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定将客户「${row.nickname}」的密码重置为 123456 吗?`, '提示', { type: 'warning' });
  } catch {
    return;
  }
  await resetCustomerPasswordApi(row.id);
  ElMessage.success('客户密码已重置为 123456');
}

/**
 * 删除客户
 */
async function handleDelete(row: CusUserInfo): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除后该客户将无法登录,历史会话仍保留在数据库中。确定删除「${row.nickname}」吗?`,
      '删除确认',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteCustomerApi(row.id);
  ElMessage.success('删除成功');
  await loadData();
}
</script>

<template>
  <div class="page-container">
    <BaseSearch :model="query" :fields="searchFields" :loading="loading" @search="search" @reset="reset" />

    <div class="card">
      <div class="card__header">
        <div>
          <h3>客户列表</h3>
          <p class="text-secondary">客户通过手机端 H5 自主注册(cus_user),与后台员工账号分离</p>
        </div>
        <span class="text-secondary">共 {{ total }} 位客户</span>
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
        <template #source="{ row }">
          <el-tag size="small" type="info" effect="plain">{{ row.registerSource }}</el-tag>
        </template>
        <template #status="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '正常' : '已禁用' }}
          </el-tag>
        </template>
        <template #action="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button link type="primary" @click="handleStatusChange(row)">
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button link type="primary" @click="handleResetPassword(row)">重置密码</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </BaseTable>
    </div>

    <BaseDialog v-model="detailVisible" title="客户详情" :show-footer="false" width="520px">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="客户ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ detail.nickname }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ detail.realName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.phone }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ detail.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="咨询次数">{{ detail.sessionCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="注册来源">{{ detail.registerSource }}</el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ detail.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="最后登录">{{ detail.lastLoginTime || '-' }}</el-descriptions-item>
      </el-descriptions>
    </BaseDialog>
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
}
</style>
