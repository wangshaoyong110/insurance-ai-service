<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import type BaseForm from '@/components/BaseForm/index.vue';
import {
  gapToKbApi,
  getGapPageApi,
  getGapStatisticsApi,
  getKbCategoriesApi,
  ignoreGapApi,
} from '@/api';
import { useTable } from '@/hooks/useTable';
import type { CategoryItem, FormField, GapGroupItem, GapStatistics, PageQuery, TableColumn } from '@/types';

/** 缺口查询条件 */
interface GapQueryParams extends PageQuery {
  keyword?: string;
  status?: number;
}

/**
 * 知识缺口
 * <p>
 * 这里放的是"客户问了、但知识库没答上来"的问题。按出现次数排序——
 * 被问得越多，越值得优先补一条标准问答。
 * </p>
 */
const categories = ref<CategoryItem[]>([]);

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
} = useTable<GapGroupItem, GapQueryParams>({
  api: getGapPageApi,
  defaultQuery: { keyword: '', status: 0 },
});

const stats = ref<GapStatistics>({
  pendingGroups: 0,
  todayRecords: 0,
  totalRecords: 0,
  topQuestions: [],
});

const categoryOptions = computed(() =>
  categories.value.map((item) => ({ label: item.name, value: item.code })),
);

const searchFields = computed<FormField[]>(() => [
  { prop: 'keyword', label: '问题', placeholder: '按问题内容搜索' },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: '待处理', value: 0 },
      { label: '已转知识', value: 1 },
      { label: '已忽略', value: 2 },
    ],
  },
]);

const columns: TableColumn<GapGroupItem>[] = [
  { prop: 'question', label: '客户提问', minWidth: 300, showOverflowTooltip: true },
  { prop: 'hitCount', label: '出现次数', width: 100, slot: 'hitCount' },
  { prop: 'bestScore', label: '召回得分', width: 100, slot: 'score' },
  { prop: 'status', label: '状态', width: 100, slot: 'status' },
  { prop: 'lastTime', label: '最近出现', width: 170 },
  { label: '操作', width: 160, slot: 'action', fixed: 'right' },
];

/** 召回得分 0 表示知识库完全没命中，越低越该补 */
function scoreType(score: number): 'danger' | 'warning' | 'success' {
  if (score === 0) {
    return 'danger';
  }
  return score < 3 ? 'warning' : 'success';
}

async function loadStatistics(): Promise<void> {
  stats.value = await getGapStatisticsApi();
}

async function refreshAll(): Promise<void> {
  await Promise.all([loadData(), loadStatistics()]);
}

/* ---------------- 转知识库 ---------------- */
const dialogVisible = ref(false);
const saving = ref(false);
const current = ref<GapGroupItem | null>(null);
const formRef = ref<InstanceType<typeof BaseForm>>();

const formModel = reactive<Record<string, unknown>>({
  category: 'OTHER',
  question: '',
  answer: '',
  keywords: '',
});

const formFields = computed<FormField[]>(() => [
  { prop: 'category', label: '业务分类', type: 'select', options: categoryOptions.value },
  { prop: 'question', label: '标准问题', disabled: true },
  { prop: 'answer', label: '标准答案', type: 'textarea', rows: 6 },
  {
    prop: 'keywords',
    label: '关键词',
    placeholder: '多个关键词用英文逗号分隔，如：理赔进度,审核时间',
  },
]);

const formRules = {
  category: [{ required: true, message: '请选择业务分类', trigger: 'change' }],
  answer: [{ required: true, message: '请输入标准答案', trigger: 'blur' }],
};

function openConvert(row: GapGroupItem): void {
  current.value = row;
  formModel.category = 'OTHER';
  formModel.question = row.question;
  formModel.answer = '';
  formModel.keywords = '';
  dialogVisible.value = true;
}

async function submitConvert(): Promise<void> {
  const valid = await formRef.value?.validate();
  if (!valid || !current.value) {
    return;
  }
  saving.value = true;
  try {
    await gapToKbApi(current.value.sampleId, {
      category: String(formModel.category),
      answer: String(formModel.answer),
      keywords: String(formModel.keywords || ''),
    });
    ElMessage.success('已生成知识条目，可在知识库管理中查看');
    dialogVisible.value = false;
    await refreshAll();
  } finally {
    saving.value = false;
  }
}

async function handleIgnore(row: GapGroupItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `忽略后该问题不再出现在待处理列表。\n${row.question}`,
      '忽略缺口',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await ignoreGapApi(row.sampleId);
  ElMessage.success('已忽略');
  await refreshAll();
}

onMounted(async () => {
  categories.value = await getKbCategoriesApi();
  await loadStatistics();
});
</script>

<template>
  <div class="page-container">
    <div class="stat-row">
      <div class="stat-block stat-block--warn">
        <p class="stat-block__label">待处理问题</p>
        <p class="stat-block__value">{{ stats.pendingGroups }}</p>
        <p class="stat-block__tip">按问题去重，都是知识库没覆盖到的</p>
      </div>
      <div class="stat-block stat-block--info">
        <p class="stat-block__label">今日新增</p>
        <p class="stat-block__value">{{ stats.todayRecords }}</p>
        <p class="stat-block__tip">今日未命中的提问条数</p>
      </div>
      <div class="stat-block">
        <p class="stat-block__label">累计记录</p>
        <p class="stat-block__value">{{ stats.totalRecords }}</p>
        <p class="stat-block__tip">含已转知识与已忽略</p>
      </div>
      <div class="stat-block stat-block--success">
        <p class="stat-block__label">高频问题 Top1</p>
        <p class="stat-block__value stat-block__value--text">
          {{ stats.topQuestions[0] || '暂无' }}
        </p>
        <p class="stat-block__tip">补语料优先从它开始</p>
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
      <div class="card__header">
        <div>
          <h3>客户问了但没答上来的问题</h3>
          <p class="text-secondary">
            召回得分为 0 表示知识库完全没命中；补一条标准问答后，同类问题下次就能命中
          </p>
        </div>
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
        <template #hitCount="{ row }">
          <span class="hit-count">{{ row.hitCount }}</span>
        </template>

        <template #score="{ row }">
          <el-tag :type="scoreType(row.bestScore)" size="small" effect="plain">
            {{ row.bestScore }}
          </el-tag>
        </template>

        <template #status="{ row }">
          <el-tag v-if="row.status === 0" type="warning" size="small">待处理</el-tag>
          <el-tag v-else-if="row.status === 1" type="success" size="small">已转知识</el-tag>
          <el-tag v-else type="info" size="small">已忽略</el-tag>
        </template>

        <template #action="{ row }">
          <el-button v-if="row.status === 0" link type="primary" @click="openConvert(row)">
            补知识
          </el-button>
          <el-button v-if="row.status === 0" link type="danger" @click="handleIgnore(row)">
            忽略
          </el-button>
          <span v-if="row.status !== 0" class="text-secondary">—</span>
        </template>
      </BaseTable>
    </div>

    <BaseDialog
      v-model="dialogVisible"
      title="补充标准问答"
      :loading="saving"
      width="680px"
      confirm-text="生成知识条目"
      @confirm="submitConvert"
    >
      <BaseForm
        ref="formRef"
        :model="formModel"
        :fields="formFields"
        :rules="formRules"
        :show-actions="false"
        label-width="88px"
      />
      <p class="dialog-tip">
        标准问题已按客户原始提问预填。答案写清楚条件、时限与例外情况，模型会据此组织回答。
      </p>
    </BaseDialog>
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

    &--text {
      font-size: 15px;
      line-height: 1.4;
      display: -webkit-box;
      -webkit-line-clamp: 1;
      line-clamp: 1;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
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

.card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;

  h3 {
    font-size: 15px;
    color: #101828;
  }

  p {
    margin-top: 6px;
    font-size: 12px;
  }
}

.hit-count {
  font-weight: 600;
  color: #b54708;
}

.dialog-tip {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.7;
  color: #98a2b3;
}
</style>
