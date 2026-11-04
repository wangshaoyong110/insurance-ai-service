<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getClaimDetailApi, getClaimPageApi, getPolicyDetailApi, getPolicyPageApi } from '@/api';
import { useTable } from '@/hooks/useTable';
import type { ClaimDetail, ClaimItem, FormField, PolicyItem, TableColumn } from '@/types';
import type { ClaimQueryParams, PolicyQueryParams } from '@/types/api';

/**
 * 保单理赔查询(管理端)
 * 只读页面:数据由核心业务系统同步,这里只做查询与进度追踪
 */
const route = useRoute();
const router = useRouter();
// 支持 /service/policy?tab=claim 直接打开理赔页签,便于分享与排查
const activeTab = ref<'policy' | 'claim'>(route.query.tab === 'claim' ? 'claim' : 'policy');

watch(activeTab, (tab) => {
  router.replace({ path: route.path, query: tab === 'claim' ? { tab: 'claim' } : {} });
});

/** 险种字典 */
const CATEGORY_MAP: Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'info' | 'danger' }> = {
  CRITICAL: { label: '重疾险', type: 'danger' },
  MEDICAL: { label: '医疗险', type: 'primary' },
  ACCIDENT: { label: '意外险', type: 'warning' },
  LIFE: { label: '寿险', type: 'success' },
  ANNUITY: { label: '年金险', type: 'info' },
  OTHER: { label: '其他', type: 'info' },
};

/** 保单状态字典 */
const POLICY_STATUS_MAP: Record<number, { label: string; type: 'success' | 'warning' | 'info' | 'danger' }> = {
  1: { label: '有效', type: 'success' },
  2: { label: '中止', type: 'warning' },
  3: { label: '失效', type: 'info' },
  4: { label: '已退保', type: 'danger' },
  5: { label: '满期终止', type: 'info' },
};

/** 理赔状态字典 */
const CLAIM_STATUS_MAP: Record<
  number,
  { label: string; type: 'success' | 'warning' | 'info' | 'danger' | 'primary' }
> = {
  1: { label: '已报案', type: 'info' },
  2: { label: '资料审核', type: 'warning' },
  3: { label: '调查中', type: 'warning' },
  4: { label: '审核通过', type: 'primary' },
  5: { label: '已赔付', type: 'success' },
  6: { label: '拒赔', type: 'danger' },
  7: { label: '已撤销', type: 'info' },
};

/** 理赔类型字典 */
const CLAIM_TYPE_MAP: Record<string, string> = {
  MEDICAL: '医疗',
  CRITICAL: '重疾',
  ACCIDENT: '意外',
  DEATH: '身故',
};

/* ---------------- 保单 ---------------- */

const {
  loading: policyLoading,
  list: policyList,
  total: policyTotal,
  query: policyQuery,
  pagination: policyPagination,
  search: policySearch,
  reset: policyReset,
  handleCurrentChange: policyPageChange,
  handleSizeChange: policySizeChange,
} = useTable<PolicyItem, PolicyQueryParams>({
  api: getPolicyPageApi,
  defaultQuery: { policyNo: '', customer: '', category: undefined, status: undefined },
});

const policySearchFields: FormField[] = [
  { prop: 'policyNo', label: '保单号', placeholder: '支持模糊查询' },
  { prop: 'customer', label: '客户', placeholder: '姓名 / 手机号' },
  {
    prop: 'category',
    label: '险种',
    type: 'select',
    options: Object.entries(CATEGORY_MAP).map(([value, item]) => ({ label: item.label, value })),
  },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: Object.entries(POLICY_STATUS_MAP).map(([value, item]) => ({ label: item.label, value: Number(value) })),
  },
];

const policyColumns: TableColumn<PolicyItem>[] = [
  { prop: 'policyNo', label: '保单号', width: 140 },
  { prop: 'customerName', label: '客户', width: 80, formatter: (row: PolicyItem) => row.customerName || '-' },
  { prop: 'productName', label: '产品名称', minWidth: 170, showOverflowTooltip: true },
  { prop: 'category', label: '险种', width: 82, slot: 'category' },
  {
    prop: 'coverageAmount',
    label: '保额(元)',
    // 120 是 3,000,000.00 这类七位数金额不折行的最小宽度,再窄就会把小数位挤到下一行
    width: 120,
    align: 'right',
    formatter: (row: PolicyItem) => formatMoney(row.coverageAmount),
  },
  {
    prop: 'premium',
    label: '年缴保费',
    width: 100,
    align: 'right',
    formatter: (row: PolicyItem) => formatMoney(row.premium),
  },
  // 保障期间改为上下两行展示:日期本身不允许折行,否则会出现 "2027-" / "01-09" 这种断裂
  { prop: 'effectiveDate', label: '保障期间', width: 124, slot: 'period' },
  { prop: 'status', label: '状态', width: 80, slot: 'status' },
  { prop: 'claimCount', label: '理赔', width: 60, align: 'center' },
  { label: '操作', width: 70, slot: 'action', fixed: 'right' },
];

/* ---------------- 理赔 ---------------- */

const {
  loading: claimLoading,
  list: claimList,
  total: claimTotal,
  query: claimQuery,
  pagination: claimPagination,
  search: claimSearch,
  reset: claimReset,
  handleCurrentChange: claimPageChange,
  handleSizeChange: claimSizeChange,
} = useTable<ClaimItem, ClaimQueryParams>({
  api: getClaimPageApi,
  defaultQuery: { claimNo: '', policyNo: '', customer: '', status: undefined },
});

const claimSearchFields: FormField[] = [
  { prop: 'claimNo', label: '理赔案号', placeholder: '支持模糊查询' },
  { prop: 'policyNo', label: '保单号', placeholder: '支持模糊查询' },
  { prop: 'customer', label: '客户', placeholder: '姓名 / 手机号' },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: Object.entries(CLAIM_STATUS_MAP).map(([value, item]) => ({ label: item.label, value: Number(value) })),
  },
];

/**
 * 理赔列宽说明:合计必须控制在表格容器内(1440 视口下约 1116px),
 * 否则右侧 fixed 的「操作」列会盖住「状态」列。
 * 「当前环节」不单独成列:它与状态语义重复,完整进度在「进度」弹窗的时间轴里。
 */
const claimColumns: TableColumn<ClaimItem>[] = [
  // 案号形如 CLM20261025003(13 位大写字母+数字),实测需 156px 才能单行
  { prop: 'claimNo', label: '理赔案号', width: 156 },
  { prop: 'customerName', label: '客户', width: 68, formatter: (row: ClaimItem) => row.customerName || '-' },
  { prop: 'policyNo', label: '保单号', width: 138 },
  { prop: 'productName', label: '产品名称', minWidth: 160, showOverflowTooltip: true },
  {
    prop: 'claimType',
    label: '类型',
    width: 58,
    formatter: (row: ClaimItem) => CLAIM_TYPE_MAP[row.claimType] || row.claimType,
  },
  { prop: 'reportDate', label: '报案日期', width: 108 },
  {
    prop: 'claimAmount',
    label: '申请金额',
    width: 100,
    align: 'right',
    formatter: (row: ClaimItem) => formatMoney(row.claimAmount),
  },
  {
    prop: 'approvedAmount',
    label: '核定赔付',
    width: 100,
    align: 'right',
    formatter: (row: ClaimItem) => (row.approvedAmount == null ? '-' : formatMoney(row.approvedAmount)),
  },
  { prop: 'status', label: '状态', width: 92, slot: 'status' },
  { label: '操作', width: 62, slot: 'action', fixed: 'right' },
];

/* ---------------- 详情 ---------------- */

const policyVisible = ref(false);
const policyDetail = ref<PolicyItem | null>(null);
const claimVisible = ref(false);
const claimDetail = ref<ClaimDetail | null>(null);
const claimDetailLoading = ref(false);

/**
 * 打开保单详情
 */
async function openPolicyDetail(row: PolicyItem): Promise<void> {
  policyDetail.value = row;
  policyVisible.value = true;
  try {
    // 列表已包含客户与理赔次数,详情接口补充完整字段
    policyDetail.value = await getPolicyDetailApi(row.id);
  } catch {
    // 详情接口失败时保留列表行数据展示
  }
}

/**
 * 打开理赔进度
 */
async function openClaimDetail(row: ClaimItem): Promise<void> {
  claimDetail.value = { claim: row, policy: null, progress: [] };
  claimVisible.value = true;
  claimDetailLoading.value = true;
  try {
    claimDetail.value = await getClaimDetailApi(row.id);
  } finally {
    claimDetailLoading.value = false;
  }
}

/**
 * 金额千分位
 */
function formatMoney(value?: number | null): string {
  if (value === null || value === undefined) {
    return '-';
  }
  return value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/**
 * 理赔节点颜色:已完成-绿色,进行中-蓝色
 */
function progressType(finished: number): 'success' | 'primary' {
  return finished === 1 ? 'success' : 'primary';
}
</script>

<template>
  <div class="page-container">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="保单查询" name="policy">
        <BaseSearch
          :model="policyQuery"
          :fields="policySearchFields"
          :loading="policyLoading"
          @search="policySearch"
          @reset="policyReset"
        />

        <div class="card">
          <div class="card__header">
            <div>
              <h3>保单列表</h3>
              <p class="text-secondary">客户在手机端提问时,AI 通过工具查询同一份数据,保证答复与后台一致</p>
            </div>
            <span class="text-secondary">共 {{ policyTotal }} 张保单</span>
          </div>

          <BaseTable
            :data="policyList"
            :columns="policyColumns"
            :loading="policyLoading"
            :total="policyTotal"
            :page-num="policyPagination.pageNum"
            :page-size="policyPagination.pageSize"
            @update:page-num="policyPageChange"
            @update:page-size="policySizeChange"
          >
            <template #category="{ row }">
              <el-tag :type="CATEGORY_MAP[row.category]?.type ?? 'info'" size="small" effect="plain">
                {{ CATEGORY_MAP[row.category]?.label ?? row.category }}
              </el-tag>
            </template>
            <template #period="{ row }">
              <div class="period-cell">
                <span>{{ row.effectiveDate }}</span>
                <span class="period-cell__end">至 {{ row.expireDate }}</span>
              </div>
            </template>
            <template #status="{ row }">
              <el-tag :type="POLICY_STATUS_MAP[row.status]?.type ?? 'info'" size="small">
                {{ POLICY_STATUS_MAP[row.status]?.label ?? row.status }}
              </el-tag>
            </template>
            <template #action="{ row }">
              <el-button link type="primary" @click="openPolicyDetail(row)">详情</el-button>
            </template>
          </BaseTable>
        </div>
      </el-tab-pane>

      <el-tab-pane label="理赔查询" name="claim">
        <BaseSearch
          :model="claimQuery"
          :fields="claimSearchFields"
          :loading="claimLoading"
          @search="claimSearch"
          @reset="claimReset"
        />

        <div class="card">
          <div class="card__header">
            <div>
              <h3>理赔列表</h3>
              <p class="text-secondary">客户询问"理赔到哪一步"时,AI 会按案号查询下方同一份进度数据</p>
            </div>
            <span class="text-secondary">共 {{ claimTotal }} 笔理赔</span>
          </div>

          <BaseTable
            :data="claimList"
            :columns="claimColumns"
            :loading="claimLoading"
            :total="claimTotal"
            :page-num="claimPagination.pageNum"
            :page-size="claimPagination.pageSize"
            @update:page-num="claimPageChange"
            @update:page-size="claimSizeChange"
          >
            <template #status="{ row }">
              <el-tag :type="CLAIM_STATUS_MAP[row.status]?.type ?? 'info'" size="small">
                {{ CLAIM_STATUS_MAP[row.status]?.label ?? row.status }}
              </el-tag>
            </template>
            <template #action="{ row }">
              <el-button link type="primary" @click="openClaimDetail(row)">进度</el-button>
            </template>
          </BaseTable>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 保单详情 -->
    <BaseDialog v-model="policyVisible" title="保单详情" :show-footer="false" width="620px">
      <el-descriptions v-if="policyDetail" :column="2" border>
        <el-descriptions-item label="保单号" :span="2">{{ policyDetail.policyNo }}</el-descriptions-item>
        <el-descriptions-item label="产品名称" :span="2">{{ policyDetail.productName }}</el-descriptions-item>
        <el-descriptions-item label="险种">
          {{ CATEGORY_MAP[policyDetail.category]?.label ?? policyDetail.category }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          {{ POLICY_STATUS_MAP[policyDetail.status]?.label ?? policyDetail.status }}
        </el-descriptions-item>
        <el-descriptions-item label="投保人">{{ policyDetail.holderName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="被保人">{{ policyDetail.insuredName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="证件号">{{ policyDetail.insuredIdCard || '-' }}</el-descriptions-item>
        <el-descriptions-item label="缴费年限">
          {{ policyDetail.payYears ? `${policyDetail.payYears} 年` : '趸交' }}
        </el-descriptions-item>
        <el-descriptions-item label="保额(元)">{{ formatMoney(policyDetail.coverageAmount) }}</el-descriptions-item>
        <el-descriptions-item label="年缴保费(元)">{{ formatMoney(policyDetail.premium) }}</el-descriptions-item>
        <el-descriptions-item label="生效日期">{{ policyDetail.effectiveDate }}</el-descriptions-item>
        <el-descriptions-item label="到期日期">{{ policyDetail.expireDate }}</el-descriptions-item>
        <el-descriptions-item label="下次缴费">{{ policyDetail.nextPayDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="历史理赔">{{ policyDetail.claimCount ?? 0 }} 次</el-descriptions-item>
        <el-descriptions-item label="所属客户" :span="2">
          {{ policyDetail.customerName || '-' }}
          <span class="text-secondary">{{ policyDetail.customerPhone || '' }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </BaseDialog>

    <!-- 理赔进度 -->
    <BaseDialog v-model="claimVisible" title="理赔进度" :show-footer="false" width="640px">
      <div v-loading="claimDetailLoading">
        <template v-if="claimDetail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="理赔案号">{{ claimDetail.claim.claimNo }}</el-descriptions-item>
            <el-descriptions-item label="关联保单">{{ claimDetail.claim.policyNo }}</el-descriptions-item>
            <el-descriptions-item label="申请金额">
              {{ formatMoney(claimDetail.claim.claimAmount) }} 元
            </el-descriptions-item>
            <el-descriptions-item label="核定赔付">
              {{ claimDetail.claim.approvedAmount == null ? '-' : `${formatMoney(claimDetail.claim.approvedAmount)} 元` }}
            </el-descriptions-item>
            <el-descriptions-item label="报案日期">{{ claimDetail.claim.reportDate }}</el-descriptions-item>
            <el-descriptions-item label="当前状态">
              <el-tag :type="CLAIM_STATUS_MAP[claimDetail.claim.status]?.type ?? 'info'" size="small">
                {{ CLAIM_STATUS_MAP[claimDetail.claim.status]?.label ?? claimDetail.claim.status }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="当前环节">{{ claimDetail.claim.currentNode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="处理人">{{ claimDetail.claim.handler || '-' }}</el-descriptions-item>
            <el-descriptions-item label="预计赔付">{{ claimDetail.claim.expectedPayDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="结案日期">{{ claimDetail.claim.closeDate || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="claimDetail.claim.rejectReason" label="拒赔原因" :span="2">
              {{ claimDetail.claim.rejectReason }}
            </el-descriptions-item>
          </el-descriptions>

          <h4 class="timeline-title">进度时间轴</h4>
          <el-empty v-if="claimDetail.progress.length === 0" description="暂无进度记录" :image-size="72" />
          <el-timeline v-else>
            <el-timeline-item
              v-for="item in claimDetail.progress"
              :key="item.id"
              :timestamp="item.progressTime"
              :type="progressType(item.finished)"
              placement="top"
            >
              <div class="timeline-node">
                <strong>{{ item.node }}</strong>
                <el-tag v-if="item.finished === 0" size="small" type="warning" effect="plain">进行中</el-tag>
              </div>
              <p class="timeline-desc">{{ item.nodeDesc || '-' }}</p>
              <p class="text-secondary timeline-operator">处理人:{{ item.operatorName || '-' }}</p>
            </el-timeline-item>
          </el-timeline>
        </template>
      </div>
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

/** 保障期间:两行堆叠,日期整体不折行 */
.period-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.45;
  white-space: nowrap;

  &__end {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}

.timeline-title {
  margin: $spacing-lg 0 $spacing-md;
  font-size: $font-size-base;
}

.timeline-node {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.timeline-desc {
  margin-top: 4px;
  font-size: 13px;
}

.timeline-operator {
  margin-top: 2px;
  font-size: 12px;
}
</style>
