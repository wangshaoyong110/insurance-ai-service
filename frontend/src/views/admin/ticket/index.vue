<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { getTicketPageApi, getTicketStatisticsApi, handleTicketApi } from '@/api';
import { useTable } from '@/hooks/useTable';
import type { FormField, TableColumn, TicketItem, TicketStatistics } from '@/types';
import type { TicketQueryParams } from '@/types/api';

/**
 * 服务工单工作台
 * <p>
 * 客户在手机端点「转人工」后，工单落到这里。列表按「待处理优先 + 紧急优先 + 新单优先」排序，
 * 坐席打开页面看到的第一行就是最该先处理的单子。
 * </p>
 */
const {
  loading,
  list,
  total,
  query,
  pagination,
  loadData,
  search,
  reset,
  handleCurrentChange,
  handleSizeChange,
} = useTable<TicketItem, TicketQueryParams>({
  api: getTicketPageApi,
  defaultQuery: { ticketNo: '', status: undefined, reason: undefined, customer: '' },
});

/** 顶部统计 */
const stats = ref<TicketStatistics>({
  pending: 0,
  processing: 0,
  finished: 0,
  todayNew: 0,
  acceptRate: 0,
});

/** 转人工原因字典 */
const reasonOptions = [
  { label: '回答不满意', value: 'DISSATISFIED' },
  { label: '投诉建议', value: 'COMPLAINT' },
  { label: '业务复杂需人工', value: 'COMPLEX' },
  { label: '其他', value: 'OTHER' },
];

const statusOptions = [
  { label: '待处理', value: 0 },
  { label: '处理中', value: 1 },
  { label: '已完成', value: 2 },
  { label: '已取消', value: 3 },
];

const searchFields = computed<FormField[]>(() => [
  { prop: 'ticketNo', label: '工单号', placeholder: '如 TK20261104' },
  { prop: 'status', label: '状态', type: 'select', options: statusOptions },
  { prop: 'reason', label: '转人工原因', type: 'select', options: reasonOptions },
  { prop: 'customer', label: '客户', placeholder: '姓名 / 手机号' },
]);

const columns: TableColumn<TicketItem>[] = [
  { prop: 'ticketNo', label: '工单号', width: 150 },
  { prop: 'customerName', label: '客户', width: 120, slot: 'customer' },
  { prop: 'question', label: '问题摘要', minWidth: 140, showOverflowTooltip: true },
  { prop: 'reason', label: '原因', width: 130, slot: 'reason' },
  { prop: 'priority', label: '优先级', width: 80, slot: 'priority' },
  { prop: 'status', label: '状态', width: 90, slot: 'status' },
  { prop: 'handlerName', label: '处理人', width: 110, slot: 'handler' },
  { prop: 'createTime', label: '提交时间', width: 165 },
  { label: '操作', width: 140, slot: 'action', fixed: 'right' },
];

function reasonName(code: string): string {
  return reasonOptions.find((item) => item.value === code)?.label ?? code ?? '-';
}

function statusName(code: number): string {
  return statusOptions.find((item) => item.value === code)?.label ?? '-';
}

/** 状态标签配色:待处理醒目、已完成收敛 */
function statusType(code: number): 'warning' | 'primary' | 'success' | 'info' {
  switch (code) {
    case 0:
      return 'warning';
    case 1:
      return 'primary';
    case 2:
      return 'success';
    default:
      return 'info';
  }
}

async function loadStatistics(): Promise<void> {
  stats.value = await getTicketStatisticsApi();
}

/** 刷新列表与统计 */
async function refreshAll(): Promise<void> {
  await Promise.all([loadData(), loadStatistics()]);
}

/* ---------------- 接单 / 取消 ---------------- */
const acting = ref(false);

async function accept(row: TicketItem): Promise<void> {
  acting.value = true;
  try {
    await handleTicketApi(row.id, { status: 1 });
    ElMessage.success(`已接单 ${row.ticketNo}`);
    await refreshAll();
  } finally {
    acting.value = false;
  }
}

async function cancel(row: TicketItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定取消工单 ${row.ticketNo} 吗?`, '取消工单', { type: 'warning' });
  } catch {
    return;
  }
  await handleTicketApi(row.id, { status: 3 });
  ElMessage.success('工单已取消');
  await refreshAll();
}

/* ---------------- 结单 ---------------- */
const finishVisible = ref(false);
const saving = ref(false);
const current = ref<TicketItem | null>(null);
const finishForm = reactive({ reply: '', priority: 1 });

function openFinish(row: TicketItem): void {
  current.value = row;
  finishForm.reply = row.reply ?? '';
  finishForm.priority = row.priority;
  finishVisible.value = true;
}

async function submitFinish(): Promise<void> {
  if (!current.value) {
    return;
  }
  if (!finishForm.reply.trim()) {
    ElMessage.warning('请填写给客户的回复');
    return;
  }
  saving.value = true;
  try {
    await handleTicketApi(current.value.id, {
      status: 2,
      reply: finishForm.reply.trim(),
      priority: finishForm.priority,
    });
    ElMessage.success('工单已结单');
    finishVisible.value = false;
    await refreshAll();
  } finally {
    saving.value = false;
  }
}

/* ---------------- 详情 ---------------- */
const detailVisible = ref(false);
const detail = ref<TicketItem | null>(null);

function openDetail(row: TicketItem): void {
  detail.value = row;
  detailVisible.value = true;
}

onMounted(loadStatistics);
</script>

<template>
  <div class="page-container">
    <!-- 顶部统计 -->
    <div class="stat-row">
      <div class="stat-block stat-block--warn">
        <p class="stat-block__label">待处理</p>
        <p class="stat-block__value">{{ stats.pending }}</p>
        <p class="stat-block__tip">客户在等坐席响应</p>
      </div>
      <div class="stat-block stat-block--info">
        <p class="stat-block__label">处理中</p>
        <p class="stat-block__value">{{ stats.processing }}</p>
        <p class="stat-block__tip">已接单未结单</p>
      </div>
      <div class="stat-block stat-block--success">
        <p class="stat-block__label">已完成</p>
        <p class="stat-block__value">{{ stats.finished }}</p>
        <p class="stat-block__tip">人工接手率 {{ stats.acceptRate }}%</p>
      </div>
      <div class="stat-block">
        <p class="stat-block__label">今日新增</p>
        <p class="stat-block__value">{{ stats.todayNew }}</p>
        <p class="stat-block__tip">今日客户转人工数量</p>
      </div>
    </div>

    <BaseSearch
      :model="query"
      :fields="searchFields"
      :loading="loading"
      @search="search"
      @reset="reset"
    />

    <div class="card">
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
        <template #customer="{ row }">
          <div class="cell-customer">
            <span>{{ row.customerName || '-' }}</span>
            <small>{{ row.customerPhone || '' }}</small>
          </div>
        </template>

        <template #reason="{ row }">
          <el-tag effect="plain" size="small" type="info">{{ reasonName(row.reason) }}</el-tag>
        </template>

        <template #priority="{ row }">
          <el-tag v-if="row.priority === 2" type="danger" size="small" effect="dark">紧急</el-tag>
          <span v-else class="text-muted">普通</span>
        </template>

        <template #status="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusName(row.status) }}</el-tag>
        </template>

        <template #handler="{ row }">
          <span :title="row.handlerName ?? ''">{{ row.handlerName || '-' }}</span>
        </template>

        <template #action="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="row.status === 0"
            link
            type="success"
            :loading="acting"
            @click="accept(row)"
          >
            接单
          </el-button>
          <el-button v-if="row.status === 1" link type="success" @click="openFinish(row)">
            结单
          </el-button>
          <el-button v-if="row.status < 2" link type="danger" @click="cancel(row)">取消</el-button>
        </template>
      </BaseTable>
    </div>

    <!-- 结单弹窗 -->
    <BaseDialog
      v-model="finishVisible"
      title="处理并结单"
      :loading="saving"
      width="560px"
      confirm-text="确认结单"
      @confirm="submitFinish"
    >
      <el-form label-width="82px">
        <el-form-item label="工单号">
          <span>{{ current?.ticketNo }}</span>
        </el-form-item>
        <el-form-item label="客户问题">
          <div class="finish-question">{{ current?.question }}</div>
        </el-form-item>
        <el-form-item label="回复客户" required>
          <el-input
            v-model="finishForm.reply"
            type="textarea"
            :rows="5"
            maxlength="1000"
            show-word-limit
            placeholder="例如:已核实您的理赔材料于 11 月 2 日签收，目前处于审核环节，预计 3 个工作日内出结论。"
          />
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="finishForm.priority">
            <el-radio :value="1">普通</el-radio>
            <el-radio :value="2">紧急</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
    </BaseDialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="工单详情" size="460px">
      <el-descriptions v-if="detail" :column="1" border size="small" class="ticket-detail">
        <el-descriptions-item label="工单号">{{ detail.ticketNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(detail.status)" size="small">{{ statusName(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="客户">
          {{ detail.customerName || '-' }} {{ detail.customerPhone || '' }}
        </el-descriptions-item>
        <el-descriptions-item label="转人工原因">{{ reasonName(detail.reason) }}</el-descriptions-item>
        <el-descriptions-item label="优先级">
          {{ detail.priority === 2 ? '紧急' : '普通' }}
        </el-descriptions-item>
        <el-descriptions-item label="问题摘要">
          <div class="detail-text">{{ detail.question }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="坐席回复">
          <div class="detail-text">{{ detail.reply || '尚未回复' }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="处理人">{{ detail.handlerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="接单时间">{{ detail.acceptTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结单时间">{{ detail.finishTime || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.stat-block {
  padding: 16px 18px;
  background: #fff;
  border-radius: 12px;
  border-left: 3px solid #d0d5dd;
  box-shadow: 0 2px 10px rgba(16, 24, 40, 0.04);

  &__label {
    font-size: 13px;
    color: #667085;
  }

  &__value {
    margin: 6px 0 4px;
    font-size: 26px;
    font-weight: 600;
    color: #101828;
    line-height: 1.1;
  }

  &__tip {
    font-size: 12px;
    color: #98a2b3;
  }

  &--warn {
    border-left-color: #f79009;
  }

  &--info {
    border-left-color: #2e90fa;
  }

  &--success {
    border-left-color: #12b76a;
  }
}

.cell-customer {
  display: flex;
  flex-direction: column;
  line-height: 1.4;

  small {
    color: #98a2b3;
    font-size: 12px;
  }
}

.text-muted {
  color: #98a2b3;
}

.finish-question,
.detail-text {
  white-space: pre-wrap;
  line-height: 1.7;
  color: #475467;
}

// 详情标签必须单行：中文标签多一个字会折行，右侧内容跟着错位
.ticket-detail {
  :deep(.el-descriptions__label) {
    width: 96px;
    white-space: nowrap;
  }
}
</style>
