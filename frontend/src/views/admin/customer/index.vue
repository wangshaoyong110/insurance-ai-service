<script setup lang="ts">
import { ref } from 'vue';
import {
  changeCustomerStatusApi,
  deleteCustomerApi,
  getCustomerPageApi,
  getCustomerProfileApi,
  resetCustomerPasswordApi,
} from '@/api';
import { useTable } from '@/hooks/useTable';
import { maskPhone } from '@/utils';
import type { CusUserInfo, CustomerProfile, FormField, TableColumn } from '@/types';
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
  { prop: 'nickname', label: '昵称', minWidth: 90 },
  { prop: 'realName', label: '姓名', minWidth: 80, formatter: (row) => row.realName || '-' },
  {
    prop: 'phone',
    label: '手机号',
    width: 120,
    formatter: (row: CusUserInfo) => maskPhone(row.phone),
  },
  { prop: 'sessionCount', label: '咨询次数', width: 90, align: 'center' },
  { prop: 'registerSource', label: '来源', width: 80, slot: 'source' },
  { prop: 'status', label: '状态', width: 80, slot: 'status' },
  // 时间列必须是 165：19 位时间串在 150 下会折成两行，整张表行高参差
  { prop: 'createTime', label: '注册时间', width: 165 },
  { prop: 'lastLoginTime', label: '最后登录', width: 165 },
  { label: '操作', width: 210, slot: 'action', fixed: 'right' },
];

/** 客户 360 抽屉 */
const profileVisible = ref(false);
const profileLoading = ref(false);
const profile = ref<CustomerProfile | null>(null);
/** 当前页签 */
const profileTab = ref('overview');

/**
 * 查看客户 360
 * <p>账号信息 + 名下保单 + 理赔 + 最近会话，坐席处理工单前不用再切页面查</p>
 */
async function openDetail(row: CusUserInfo): Promise<void> {
  profileVisible.value = true;
  profileTab.value = 'overview';
  profile.value = null;
  profileLoading.value = true;
  try {
    profile.value = await getCustomerProfileApi(row.id);
  } finally {
    profileLoading.value = false;
  }
}

/** 金额转万元展示 */
function toWan(value?: number | null): string {
  if (value === null || value === undefined) {
    return '-';
  }
  return `${(value / 10000).toFixed(value % 10000 === 0 ? 0 : 1)} 万`;
}

/** 保单状态 */
const policyStatusText: Record<number, string> = {
  1: '有效',
  2: '中止',
  3: '失效',
  4: '已退保',
  5: '满期终止',
};

/** 理赔状态 */
const claimStatusText: Record<number, string> = {
  1: '已报案',
  2: '资料审核',
  3: '调查中',
  4: '审核通过',
  5: '已赔付',
  6: '拒赔',
  7: '已撤销',
};

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
          <el-button link type="primary" @click="openDetail(row)">客户360</el-button>
          <el-button link type="primary" @click="handleStatusChange(row)">
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button link type="primary" @click="handleResetPassword(row)">重置密码</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </BaseTable>
    </div>

    <!-- 客户 360：账号 + 保单 + 理赔 + 会话 -->
    <el-drawer v-model="profileVisible" title="客户 360" size="720px">
      <div v-loading="profileLoading" class="profile">
        <template v-if="profile">
          <!-- 概览指标 -->
          <div class="profile__summary">
            <div class="summary-item">
              <p class="summary-item__label">在保保单</p>
              <p class="summary-item__value">
                {{ profile.summary.activePolicyCount }}
                <small>/ {{ profile.summary.policyCount }}</small>
              </p>
            </div>
            <div class="summary-item">
              <p class="summary-item__label">有效保额</p>
              <p class="summary-item__value">{{ toWan(profile.summary.activeCoverage) }}</p>
            </div>
            <div class="summary-item">
              <p class="summary-item__label">理赔记录</p>
              <p class="summary-item__value">
                {{ profile.summary.claimCount }}
                <small v-if="profile.summary.openClaimCount > 0">
                  · 进行中 {{ profile.summary.openClaimCount }}
                </small>
              </p>
            </div>
            <div class="summary-item">
              <p class="summary-item__label">咨询会话</p>
              <p class="summary-item__value">{{ profile.summary.sessionCount ?? 0 }}</p>
            </div>
          </div>

          <el-tabs v-model="profileTab">
            <el-tab-pane label="账号信息" name="overview">
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="客户ID">{{ profile.customer.id }}</el-descriptions-item>
                <el-descriptions-item label="昵称">{{ profile.customer.nickname }}</el-descriptions-item>
                <el-descriptions-item label="姓名">{{ profile.customer.realName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="手机号">{{ profile.customer.phone }}</el-descriptions-item>
                <el-descriptions-item label="邮箱">{{ profile.customer.email || '-' }}</el-descriptions-item>
                <el-descriptions-item label="状态">
                  <el-tag :type="profile.customer.status === 1 ? 'success' : 'danger'" size="small">
                    {{ profile.customer.status === 1 ? '正常' : '已禁用' }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="注册来源">{{ profile.customer.registerSource }}</el-descriptions-item>
                <el-descriptions-item label="注册时间">{{ profile.customer.createTime || '-' }}</el-descriptions-item>
                <el-descriptions-item label="最后登录">{{ profile.customer.lastLoginTime || '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>

            <el-tab-pane :label="`保单 (${profile.policies.length})`" name="policy">
              <el-empty v-if="profile.policies.length === 0" description="该客户暂无保单" :image-size="70" />
              <div v-for="item in profile.policies" :key="item.id" class="biz-card">
                <div class="biz-card__head">
                  <span class="biz-card__title">{{ item.productName }}</span>
                  <el-tag size="small" :type="item.status === 1 ? 'success' : 'info'" effect="plain">
                    {{ policyStatusText[item.status] || item.status }}
                  </el-tag>
                </div>
                <p class="biz-card__no">{{ item.policyNo }}</p>
                <div class="biz-card__row">
                  <span>保额 {{ toWan(item.coverageAmount) }}</span>
                  <span>年缴 {{ item.premium }} 元</span>
                  <span>理赔 {{ item.claimCount ?? 0 }} 次</span>
                </div>
                <div class="biz-card__row biz-card__row--muted">
                  <span>{{ item.effectiveDate }} 至 {{ item.expireDate }}</span>
                  <span v-if="item.nextPayDate">下次缴费 {{ item.nextPayDate }}</span>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane :label="`理赔 (${profile.claims.length})`" name="claim">
              <el-empty v-if="profile.claims.length === 0" description="该客户暂无理赔记录" :image-size="70" />
              <div v-for="item in profile.claims" :key="item.id" class="biz-card">
                <div class="biz-card__head">
                  <span class="biz-card__title">{{ item.claimNo }}</span>
                  <el-tag size="small" effect="plain" type="warning">
                    {{ claimStatusText[item.status] || item.status }}
                  </el-tag>
                </div>
                <div class="biz-card__row">
                  <span>保单 {{ item.policyNo }}</span>
                  <span>报案 {{ item.reportDate }}</span>
                </div>
                <div class="biz-card__row biz-card__row--muted">
                  <span>申请 {{ item.claimAmount }} 元</span>
                  <span v-if="item.approvedAmount !== null && item.approvedAmount !== undefined">
                    核定 {{ item.approvedAmount }} 元
                  </span>
                  <span v-if="item.currentNode">环节 {{ item.currentNode }}</span>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane :label="`最近会话 (${profile.sessions.length})`" name="session">
              <el-empty v-if="profile.sessions.length === 0" description="该客户暂无咨询记录" :image-size="70" />
              <div v-for="item in profile.sessions" :key="item.id" class="session-row">
                <p class="session-row__title">{{ item.title }}</p>
                <p class="session-row__msg">{{ item.lastMessage || '暂无消息' }}</p>
                <p class="session-row__time">{{ item.lastMessageTime }} · {{ item.messageCount }} 条</p>
              </div>
            </el-tab-pane>
          </el-tabs>
        </template>
      </div>
    </el-drawer>
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

/* ---------------- 客户 360 ---------------- */
.profile {
  min-height: 200px;

  &__summary {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 10px;
    margin-bottom: 16px;
  }
}

.summary-item {
  padding: 12px;
  background: #f7f9fc;
  border-radius: 10px;

  &__label {
    font-size: 12px;
    color: #667085;
  }

  &__value {
    margin-top: 6px;
    font-size: 18px;
    font-weight: 600;
    color: #101828;

    small {
      font-size: 12px;
      font-weight: 400;
      color: #98a2b3;
    }
  }
}

.biz-card {
  margin-bottom: 10px;
  padding: 12px 14px;
  border: 1px solid #eef1f6;
  border-radius: 10px;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
  }

  &__title {
    font-size: 14px;
    font-weight: 600;
    color: #101828;
  }

  &__no {
    margin: 4px 0 8px;
    font-size: 12px;
    color: #98a2b3;
  }

  &__row {
    display: flex;
    flex-wrap: wrap;
    gap: 14px;
    margin-top: 6px;
    font-size: 12px;
    color: #475467;

    &--muted {
      color: #98a2b3;
    }
  }
}

.session-row {
  padding: 10px 0;
  border-bottom: 1px solid #f3f5f9;

  &:last-child {
    border-bottom: none;
  }

  &__title {
    font-size: 13px;
    font-weight: 600;
    color: #101828;
  }

  &__msg {
    margin: 4px 0;
    font-size: 12px;
    color: #667085;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  &__time {
    font-size: 11px;
    color: #98a2b3;
  }
}
</style>
