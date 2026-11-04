<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import type { FormRules } from 'element-plus';
import type BaseForm from '@/components/BaseForm/index.vue';
import { createKbApi, deleteKbApi, getKbCategoriesApi, getKbPageApi, updateKbApi } from '@/api';
import { useTable } from '@/hooks/useTable';
import type { CategoryItem, FormField, KbItem, TableColumn } from '@/types';
import type { KbQueryParams } from '@/types/api';

/**
 * 知识库管理:维护 AI 回答所引用的标准问答
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
} = useTable<KbItem, KbQueryParams>({
  api: getKbPageApi,
  defaultQuery: { keyword: '', category: undefined, status: undefined },
});

const categoryOptions = computed(() =>
  categories.value.map((item) => ({ label: item.name, value: item.code })),
);

const searchFields = computed<FormField[]>(() => [
  { prop: 'keyword', label: '关键字', placeholder: '问题 / 答案 / 关键词' },
  { prop: 'category', label: '业务分类', type: 'select', options: categoryOptions.value },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: '启用', value: 1 },
      { label: '停用', value: 0 },
    ],
  },
]);

const columns: TableColumn<KbItem>[] = [
  { prop: 'id', label: 'ID', width: 70 },
  { prop: 'category', label: '分类', width: 100, slot: 'category' },
  { prop: 'question', label: '标准问题', minWidth: 220, showOverflowTooltip: true },
  { prop: 'keywords', label: '关键词', minWidth: 160, showOverflowTooltip: true },
  { prop: 'hitCount', label: '命中次数', width: 100 },
  { prop: 'status', label: '状态', width: 90, slot: 'status' },
  { prop: 'updateTime', label: '更新时间', width: 170 },
  { label: '操作', width: 180, slot: 'action', fixed: 'right' },
];

/** 分类名称映射 */
function categoryName(code: string): string {
  return categories.value.find((item) => item.code === code)?.name ?? code;
}

/* ---------------- 新增 / 编辑 ---------------- */
const dialogVisible = ref(false);
const saving = ref(false);
const currentId = ref<number | null>(null);
const formRef = ref<InstanceType<typeof BaseForm>>();

const dialogTitle = computed<string>(() => (currentId.value ? '编辑知识' : '新增知识'));

const formModel = reactive<Record<string, unknown>>({
  category: 'INSURE',
  question: '',
  answer: '',
  keywords: '',
  status: 1,
  remark: '',
});

const formFields = computed<FormField[]>(() => [
  { prop: 'category', label: '业务分类', type: 'select', span: 12, options: categoryOptions.value },
  { prop: 'status', label: '启用状态', type: 'switch', span: 12 },
  { prop: 'question', label: '标准问题', maxlength: 200 },
  { prop: 'answer', label: '标准答案', type: 'textarea', rows: 6 },
  { prop: 'keywords', label: '关键词', placeholder: '多个关键词用英文逗号分隔,如:退保,现金价值' },
  { prop: 'remark', label: '备注', type: 'textarea', rows: 2, maxlength: 200 },
]);

const formRules: FormRules = {
  category: [{ required: true, message: '请选择业务分类', trigger: 'change' }],
  question: [{ required: true, message: '请输入标准问题', trigger: 'blur' }],
  answer: [{ required: true, message: '请输入标准答案', trigger: 'blur' }],
};

function openCreate(): void {
  currentId.value = null;
  formModel.category = 'INSURE';
  formModel.question = '';
  formModel.answer = '';
  formModel.keywords = '';
  formModel.status = 1;
  formModel.remark = '';
  dialogVisible.value = true;
}

function openEdit(row: KbItem): void {
  currentId.value = row.id;
  formModel.category = row.category;
  formModel.question = row.question;
  formModel.answer = row.answer;
  formModel.keywords = row.keywords ?? '';
  formModel.status = row.status;
  formModel.remark = row.remark ?? '';
  dialogVisible.value = true;
}

async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate();
  if (!valid) {
    return;
  }
  saving.value = true;
  try {
    const payload = {
      category: String(formModel.category),
      question: String(formModel.question),
      answer: String(formModel.answer),
      keywords: String(formModel.keywords ?? ''),
      status: Number(formModel.status ?? 1),
      remark: String(formModel.remark ?? ''),
    };
    if (currentId.value) {
      await updateKbApi(currentId.value, payload);
      ElMessage.success('修改成功');
    } else {
      await createKbApi(payload);
      ElMessage.success('新增成功');
    }
    dialogVisible.value = false;
    await loadData();
  } finally {
    saving.value = false;
  }
}

async function handleDelete(row: KbItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除该条知识吗?\n${row.question}`, '删除知识', { type: 'warning' });
  } catch {
    return;
  }
  await deleteKbApi(row.id);
  ElMessage.success('删除成功');
  await loadData();
}

/* ---------------- 答案预览 ---------------- */
const previewVisible = ref(false);
const previewItem = ref<KbItem | null>(null);

function openPreview(row: KbItem): void {
  previewItem.value = row;
  previewVisible.value = true;
}

onMounted(async () => {
  categories.value = await getKbCategoriesApi();
});
</script>

<template>
  <div class="page-container">
    <BaseSearch
      :model="query"
      :fields="searchFields"
      :loading="loading"
      @search="search"
      @reset="reset"
    >
      <template #actions>
        <el-button type="success" @click="openCreate">
          <el-icon><Plus /></el-icon>新增知识
        </el-button>
      </template>
    </BaseSearch>

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
        <template #category="{ row }">
          <el-tag effect="plain" size="small">{{ categoryName(row.category) }}</el-tag>
        </template>

        <template #status="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>

        <template #action="{ row }">
          <el-button link type="primary" @click="openPreview(row)">查看</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </BaseTable>
    </div>

    <BaseDialog
      v-model="dialogVisible"
      :title="dialogTitle"
      :loading="saving"
      width="680px"
      @confirm="handleSubmit"
    >
      <BaseForm
        ref="formRef"
        :model="formModel"
        :fields="formFields"
        :rules="formRules"
        :show-actions="false"
        label-width="88px"
      />
    </BaseDialog>

    <BaseDialog v-model="previewVisible" title="知识详情" width="640px" :show-footer="false">
      <div v-if="previewItem" class="preview">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="业务分类">
            {{ categoryName(previewItem.category) }}
          </el-descriptions-item>
          <el-descriptions-item label="命中次数">{{ previewItem.hitCount }}</el-descriptions-item>
          <el-descriptions-item label="关键词" :span="2">
            {{ previewItem.keywords || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="标准问题" :span="2">
            {{ previewItem.question }}
          </el-descriptions-item>
          <el-descriptions-item label="标准答案" :span="2">
            <div class="preview__answer">{{ previewItem.answer }}</div>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </BaseDialog>
  </div>
</template>

<style scoped lang="scss">
.preview {
  &__answer {
    white-space: pre-wrap;
    line-height: 1.9;
  }
}
</style>
