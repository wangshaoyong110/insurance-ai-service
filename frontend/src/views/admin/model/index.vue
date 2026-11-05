<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import {
  changeAiModelStatusApi,
  createAiModelApi,
  deleteAiModelApi,
  getAiModelListApi,
  setDefaultAiModelApi,
  testAiModelApi,
  updateAiModelApi,
} from '@/api';
import type { AiModelItem, TableColumn } from '@/types';
import type { AiModelSaveParams } from '@/types/api';

/**
 * 大模型配置(管理端)
 * - API Key 由后端国密 SM4-GCM 加密后入库,列表只返回掩码
 * - 改完立即生效:对话时按数据库中的默认配置构建 ChatModel,无需重启
 */

/** 提供方字典 */
const PROVIDER_MAP: Record<string, { label: string; type: 'primary' | 'success'; hint: string }> = {
  OPENAI_COMPATIBLE: {
    label: 'OpenAI 兼容',
    type: 'primary',
    hint: 'DeepSeek / vLLM / Ollama / 行内模型网关,只要兼容 OpenAI 协议即可',
  },
  DASHSCOPE: {
    label: '阿里百炼',
    type: 'success',
    hint: '通义千问系列,地址默认 https://dashscope.aliyuncs.com',
  },
};

const loading = ref(false);
const list = ref<AiModelItem[]>([]);

/** 表格列 */
const columns: TableColumn<AiModelItem>[] = [
  { prop: 'configName', label: '配置名称', minWidth: 120 },
  { prop: 'provider', label: '提供方', width: 130, slot: 'provider' },
  { prop: 'model', label: '模型', width: 125 },
  {
    prop: 'baseUrl',
    label: '接口地址',
    minWidth: 150,
    showOverflowTooltip: true,
    formatter: (row: AiModelItem) => row.baseUrl || '(厂商默认)',
  },
  { prop: 'apiKeyMask', label: 'API Key', width: 115, formatter: (row: AiModelItem) => row.apiKeyMask || '-' },
  { prop: 'temperature', label: '温度', width: 60, align: 'center' },
  { prop: 'isDefault', label: '默认', width: 65, slot: 'isDefault' },
  { prop: 'status', label: '启用', width: 70, slot: 'status' },
  { label: '操作', width: 200, slot: 'action', fixed: 'right' },
];

/**
 * 渲染列内容(el-table 插槽的 row 类型为 DefaultRow,此处统一收窄)
 */
function renderCell(col: TableColumn<AiModelItem>, row: unknown, index: number): string {
  if (!col.formatter) {
    return '';
  }
  const record = row as Record<string, unknown>;
  const value = col.prop ? record[col.prop] : undefined;
  return col.formatter(row as AiModelItem, value, index);
}

/**
 * 加载配置列表
 */
async function loadData(): Promise<void> {
  loading.value = true;
  try {
    list.value = await getAiModelListApi();
  } finally {
    loading.value = false;
  }
}

onMounted(loadData);

/* ---------------- 表单 ---------------- */

const dialogVisible = ref(false);
const submitting = ref(false);
const testing = ref(false);
const formRef = ref<FormInstance>();
const isEdit = computed(() => form.id !== undefined && form.id !== null);

const form = reactive<AiModelSaveParams>({
  id: undefined,
  configName: '',
  provider: 'OPENAI_COMPATIBLE',
  baseUrl: '',
  apiKey: '',
  model: '',
  temperature: 0.3,
  maxTokens: 1024,
  timeoutMs: 60000,
  isDefault: 0,
  status: 1,
  remark: '',
});

const rules: FormRules = {
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  provider: [{ required: true, message: '请选择提供方', trigger: 'change' }],
  model: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
};

/** 当前提供方提示 */
const providerHint = computed(() => PROVIDER_MAP[form.provider]?.hint ?? '');

/**
 * 重置表单
 */
function resetForm(): void {
  Object.assign(form, {
    id: undefined,
    configName: '',
    provider: 'OPENAI_COMPATIBLE',
    baseUrl: '',
    apiKey: '',
    model: '',
    temperature: 0.3,
    maxTokens: 1024,
    timeoutMs: 60000,
    isDefault: 0,
    status: 1,
    remark: '',
  });
  formRef.value?.clearValidate();
}

/**
 * 新增
 */
function handleCreate(): void {
  resetForm();
  dialogVisible.value = true;
}

/**
 * 编辑
 */
function handleEdit(row: unknown): void {
  const item = row as AiModelItem;
  Object.assign(form, {
    id: item.id,
    configName: item.configName,
    provider: item.provider,
    baseUrl: item.baseUrl ?? '',
    apiKey: '',
    model: item.model,
    temperature: Number(item.temperature),
    maxTokens: item.maxTokens,
    timeoutMs: item.timeoutMs,
    isDefault: item.isDefault,
    status: item.status,
    remark: item.remark ?? '',
  });
  formRef.value?.clearValidate();
  dialogVisible.value = true;
}

/**
 * 提交
 */
async function handleSubmit(): Promise<void> {
  if (!formRef.value) {
    return;
  }
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }
  submitting.value = true;
  try {
    if (isEdit.value) {
      await updateAiModelApi(form.id as number, { ...form });
      ElMessage.success('配置已更新,新配置立即生效');
    } else {
      await createAiModelApi({ ...form });
      ElMessage.success('配置已新增');
    }
    dialogVisible.value = false;
    await loadData();
  } finally {
    submitting.value = false;
  }
}

/**
 * 连通性测试:表单内测试(未保存的配置也可测)
 */
async function handleTestForm(): Promise<void> {
  if (!form.provider || !form.model) {
    ElMessage.warning('请先填写提供方与模型名称');
    return;
  }
  if (!isEdit.value && !form.apiKey) {
    ElMessage.warning('请输入 API Key 后再测试');
    return;
  }
  testing.value = true;
  try {
    const result = await testAiModelApi({
      id: form.id,
      provider: form.provider,
      baseUrl: form.baseUrl || undefined,
      apiKey: form.apiKey || undefined,
      model: form.model,
    });
    ElMessageBox.alert(
      `耗时 ${result.costMs} ms<br/>模型回复:${result.reply || '(空)'}`,
      '连通性测试通过',
      { dangerouslyUseHTMLString: true, type: 'success' },
    );
  } finally {
    testing.value = false;
  }
}

/**
 * 列表内测试已保存配置
 */
async function handleTestRow(row: unknown): Promise<void> {
  const item = row as AiModelItem;
  const result = await testAiModelApi({ id: item.id });
  ElMessage.success(`连通正常,耗时 ${result.costMs} ms,回复:${result.reply || '(空)'}`);
}

/**
 * 启用 / 停用
 */
async function handleStatusChange(row: unknown): Promise<void> {
  const item = row as AiModelItem;
  const nextStatus = item.status === 1 ? 0 : 1;
  await changeAiModelStatusApi(item.id, nextStatus);
  ElMessage.success(nextStatus === 1 ? '已启用' : '已停用');
  await loadData();
}

/**
 * 设为默认
 */
async function handleSetDefault(row: unknown): Promise<void> {
  const item = row as AiModelItem;
  await setDefaultAiModelApi(item.id);
  ElMessage.success(`已将「${item.configName}」设为默认模型,后续对话立即使用`);
  await loadData();
}

/**
 * 删除
 */
async function handleDelete(row: unknown): Promise<void> {
  const item = row as AiModelItem;
  try {
    await ElMessageBox.confirm(`确定删除配置「${item.configName}」吗?删除后不可恢复。`, '删除确认', {
      type: 'warning',
    });
  } catch {
    return;
  }
  await deleteAiModelApi(item.id);
  ElMessage.success('已删除');
  await loadData();
}
</script>

<template>
  <div class="page-container">
    <el-alert type="info" :closable="false" show-icon class="tip">
      <template #title>
        API Key 使用国密 SM4-GCM 加密后存入数据库,页面只展示掩码;配置保存后立即生效,无需重启服务。
      </template>
      <div class="tip__desc">
        内网部署时把「接口地址」指向行内模型网关即可(兼容 OpenAI 协议),模型与地址都可在本页调整。
      </div>
    </el-alert>

    <div class="card">
      <div class="card__header">
        <div>
          <h3>模型配置</h3>
          <p class="text-secondary">当前共 {{ list.length }} 套配置,对话使用「默认 + 启用」的那一套</p>
        </div>
        <el-button type="primary" @click="handleCreate">新增配置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column
          v-for="col in columns"
          :key="col.prop ?? col.label"
          :prop="col.prop"
          :label="col.label"
          :width="col.width"
          :min-width="col.minWidth"
          :align="col.align"
          :fixed="col.fixed"
          :show-overflow-tooltip="col.showOverflowTooltip"
        >
          <template v-if="col.slot" #default="{ row }">
            <template v-if="col.slot === 'provider'">
              <el-tag :type="PROVIDER_MAP[row.provider]?.type ?? 'info'" size="small" effect="plain">
                {{ PROVIDER_MAP[row.provider]?.label ?? row.provider }}
              </el-tag>
            </template>
            <template v-else-if="col.slot === 'isDefault'">
              <el-tag v-if="row.isDefault === 1" type="danger" size="small">默认</el-tag>
              <span v-else class="text-secondary">-</span>
            </template>
            <template v-else-if="col.slot === 'status'">
              <el-switch
                :model-value="row.status === 1"
                size="small"
                @change="handleStatusChange(row)"
              />
            </template>
            <template v-else-if="col.slot === 'action'">
              <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
              <el-button link type="primary" @click="handleTestRow(row)">测试</el-button>
              <el-button link type="primary" :disabled="row.isDefault === 1" @click="handleSetDefault(row)">
                设为默认
              </el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </template>
          <template v-else-if="col.formatter" #default="{ row, $index }">
            {{ renderCell(col, row, $index) }}
          </template>
        </el-table-column>
      </el-table>
    </div>

    <BaseDialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑模型配置' : '新增模型配置'"
      width="640px"
      :loading="submitting"
      @confirm="handleSubmit"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="form.configName" maxlength="64" placeholder="如 DeepSeek-生产" />
        </el-form-item>

        <el-form-item label="提供方" prop="provider">
          <el-select v-model="form.provider" style="width: 100%">
            <el-option
              v-for="(item, key) in PROVIDER_MAP"
              :key="key"
              :label="item.label"
              :value="key"
            />
          </el-select>
          <div class="form-hint">{{ providerHint }}</div>
        </el-form-item>

        <el-form-item label="模型名称" prop="model">
          <el-input v-model="form.model" maxlength="64" placeholder="deepseek-chat / qwen-plus / 内网模型名" />
        </el-form-item>

        <el-form-item label="接口地址">
          <el-input v-model="form.baseUrl" maxlength="255" placeholder="留空使用厂商默认地址;内网部署填行内网关地址" />
        </el-form-item>

        <el-form-item label="API Key">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            maxlength="500"
            :placeholder="isEdit ? '留空表示不修改(当前:' + (list.find((i) => i.id === form.id)?.apiKeyMask ?? '-') + ')' : '请输入 API Key'"
          />
          <div class="form-hint">保存时加密入库,接口永不返回明文</div>
        </el-form-item>

        <el-form-item label="采样温度">
          <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="2" />
          <span class="form-hint inline">保险场景建议 0.1~0.4,越低越严谨</span>
        </el-form-item>

        <el-form-item label="最大 Token">
          <el-input-number v-model="form.maxTokens" :min="1" :max="100000" :step="128" />
        </el-form-item>

        <el-form-item label="读超时(ms)">
          <el-input-number v-model="form.timeoutMs" :min="1000" :max="300000" :step="1000" />
        </el-form-item>

        <el-form-item label="默认配置">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
          <span class="form-hint inline">同一时刻只会有一套默认配置</span>
        </el-form-item>

        <el-form-item label="启用">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button :loading="testing" @click="handleTestForm">测试连通性</el-button>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保 存</el-button>
      </template>
    </BaseDialog>
  </div>
</template>

<style scoped lang="scss">
.tip {
  margin-bottom: $spacing-md;
}

.tip__desc {
  margin-top: 4px;
  font-size: 12px;
}

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

.form-hint {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  color: $color-text-secondary;

  &.inline {
    width: auto;
    margin-top: 0;
    margin-left: $spacing-sm;
  }
}
</style>
