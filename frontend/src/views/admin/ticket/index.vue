<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import {
  exportTicketExcelApi,
  getAdminSessionMessagesApi,
  getTicketPageApi,
  getTicketStatisticsApi,
  handleTicketApi,
  sendStaffMessageApi,
  uploadFileApi,
} from '@/api';
import { useTable } from '@/hooks/useTable';
import { CHAT_IMAGE_ACCEPT, CHAT_VIDEO_ACCEPT, checkUpload, formatFileSize } from '@/utils/uploadLimit';
import type {
  ChatMessageItem,
  FormField,
  TableColumn,
  TicketItem,
  TicketStatistics,
  UploadResult,
} from '@/types';
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
  { label: '操作', width: 200, slot: 'action', fixed: 'right' },
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
/** 导出中:防止连点重复下载 */
const exporting = ref(false);

/**
 * 按当前筛选条件导出 Excel
 * <p>导出的是"看到什么就导出什么":条件与列表同源。注意 query 是 useTable 返回的 Ref,
 * 必须取 .value 展开成纯对象——直接把 Ref 传给请求层,内部依赖链会让参数无法 JSON 序列化。</p>
 */
async function exportExcel(): Promise<void> {
  if (exporting.value) {
    return;
  }
  exporting.value = true;
  try {
    const blob = await exportTicketExcelApi({
      ...query.value,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
    } as TicketQueryParams);
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `服务工单_${new Date().toISOString().slice(0, 10).replace(/-/g, '')}.xlsx`;
    link.click();
    URL.revokeObjectURL(url);
    ElMessage.success('导出成功');
  } catch {
    // 失败提示已由请求拦截器统一给出,这里只保证按钮状态能复位
  } finally {
    exporting.value = false;
  }
}

async function accept(row: TicketItem): Promise<void> {
  acting.value = true;
  try {
    const result = await handleTicketApi(row.id, { status: 1 });
    ElMessage.success(`已接单 ${row.ticketNo}`);
    await refreshAll();
    // 接单即接管:直接打开会话面板,坐席不用再回列表找入口
    await openChat(result);
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

/* ---------------- 接入会话 ---------------- */
const chatVisible = ref(false);
const chatTicket = ref<TicketItem | null>(null);
const chatMessages = ref<ChatMessageItem[]>([]);
const chatLoading = ref(false);
const chatSending = ref(false);
const chatUploading = ref(false);
const chatInput = ref('');
const chatBody = ref<HTMLElement>();
const imageInput = ref<HTMLInputElement>();
const videoInput = ref<HTMLInputElement>();

/** 只有处理中的工单可以发言；已结单仍可回看历史，但输入框收起 */
const chatReadonly = computed<boolean>(() => chatTicket.value?.status !== 1);

let chatTimer = 0;
let chatPolling = false;

async function scrollChat(): Promise<void> {
  await nextTick();
  const el = chatBody.value;
  if (el) {
    el.scrollTop = el.scrollHeight;
  }
}

function maxChatId(): number {
  return chatMessages.value.reduce((max, item) => Math.max(max, item.id > 0 ? item.id : 0), 0);
}

/**
 * 加载会话消息
 * @param reset true-全量重载(打开面板时);false-按游标增量
 */
async function loadChat(reset = false): Promise<void> {
  if (!chatTicket.value?.sessionId) {
    return;
  }
  const list = await getAdminSessionMessagesApi(
    chatTicket.value.sessionId,
    reset ? 0 : maxChatId(),
  );
  if (reset) {
    chatMessages.value = list;
  } else if (list.length) {
    const known = new Set(chatMessages.value.filter((item) => item.id > 0).map((item) => item.id));
    chatMessages.value.push(...list.filter((item) => !known.has(item.id)));
  }
  await scrollChat();
}

/**
 * 打开坐席会话面板(接单后自动调用)
 */
async function openChat(row: TicketItem): Promise<void> {
  if (!row.sessionId) {
    ElMessage.warning('该工单未关联咨询会话');
    return;
  }
  chatTicket.value = row;
  chatVisible.value = true;
  chatInput.value = '';
  chatLoading.value = true;
  try {
    await loadChat(true);
  } finally {
    chatLoading.value = false;
  }
  startChatPolling();
}

function stopChatPolling(): void {
  if (chatTimer) {
    window.clearInterval(chatTimer);
    chatTimer = 0;
  }
}

function startChatPolling(): void {
  stopChatPolling();
  chatTimer = window.setInterval(() => {
    void pollChat();
  }, 3000);
}

async function pollChat(): Promise<void> {
  if (!chatVisible.value || chatPolling || !chatTicket.value?.sessionId) {
    return;
  }
  chatPolling = true;
  try {
    await loadChat(false);
  } catch {
    // 轮询失败静默,下一轮重试
  } finally {
    chatPolling = false;
  }
}

async function sendChat(): Promise<void> {
  const content = chatInput.value.trim();
  if (!content || chatSending.value || !chatTicket.value?.sessionId) {
    return;
  }
  chatSending.value = true;
  try {
    const saved = await sendStaffMessageApi(chatTicket.value.sessionId, { content, msgType: 'text' });
    chatMessages.value.push(saved);
    chatInput.value = '';
    await scrollChat();
  } finally {
    chatSending.value = false;
  }
}

function pickChatMedia(kind: 'image' | 'video'): void {
  if (chatUploading.value) {
    return;
  }
  (kind === 'image' ? imageInput.value : videoInput.value)?.click();
}

async function handleChatFile(event: Event, kind: 'image' | 'video'): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file || !chatTicket.value?.sessionId) {
    return;
  }
  const error = checkUpload(file, kind === 'image' ? 'chat-image' : 'chat-video');
  if (error) {
    ElMessage.warning(error);
    return;
  }
  chatUploading.value = true;
  try {
    const uploaded: UploadResult = await uploadFileApi(file, 'chat');
    const saved = await sendStaffMessageApi(chatTicket.value.sessionId, {
      msgType: kind,
      mediaUrl: uploaded.url,
      mediaName: uploaded.name,
      mediaSize: uploaded.size,
      content: '',
    });
    chatMessages.value.push(saved);
    await scrollChat();
  } finally {
    chatUploading.value = false;
  }
}

function closeChat(): void {
  chatVisible.value = false;
  chatMessages.value = [];
  stopChatPolling();
}

onBeforeUnmount(stopChatPolling);

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
    >
      <template #actions>
        <el-button type="success" plain :loading="exporting" @click="exportExcel">
          <el-icon><Download /></el-icon>
          导出 Excel
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
          <el-button v-if="row.sessionId" link type="primary" @click="openChat(row)">会话</el-button>
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

    <!-- 坐席会话抽屉：与客户共用同一个会话，历史上下文都在这里 -->
    <el-drawer
      v-model="chatVisible"
      :title="chatTicket ? `会话 · ${chatTicket.ticketNo}` : '会话'"
      size="560px"
      @close="closeChat"
    >
      <div class="chat-panel">
        <div class="chat-panel__head">
          <span class="chat-panel__customer">
            {{ chatTicket?.customerName || '-' }}
            <small>{{ chatTicket?.customerPhone || '' }}</small>
          </span>
          <el-tag v-if="chatTicket" :type="chatTicket.status === 1 ? 'success' : 'info'" size="small">
            {{ chatTicket.status === 1 ? '处理中 · 可直接回复' : '只读 · 工单不在处理中' }}
          </el-tag>
        </div>

        <div ref="chatBody" v-loading="chatLoading" class="chat-panel__body">
          <template v-for="m in chatMessages" :key="m.id">
            <p v-if="m.role === 'system'" class="chat-sys">{{ m.content }}</p>
            <div v-else class="chat-msg" :class="`chat-msg--${m.role}`">
              <div class="chat-msg__meta">
                <span>
                  {{
                    m.role === 'staff'
                      ? m.senderName || '我'
                      : m.role === 'user'
                        ? chatTicket?.customerName || '客户'
                        : 'AI 助手'
                  }}
                </span>
                <span>{{ m.createTime }}</span>
              </div>
              <div class="chat-msg__bubble">
                <el-image
                  v-if="m.msgType === 'image' && m.mediaUrl"
                  :src="m.mediaUrl"
                  :preview-src-list="[m.mediaUrl]"
                  fit="cover"
                  preview-teleported
                  class="chat-msg__image"
                />
                <video
                  v-else-if="m.msgType === 'video' && m.mediaUrl"
                  :src="m.mediaUrl"
                  class="chat-msg__video"
                  controls
                ></video>
                <p v-else class="chat-msg__text">{{ m.content }}</p>
                <span v-if="m.mediaSize" class="chat-msg__size">{{ formatFileSize(m.mediaSize) }}</span>
              </div>
            </div>
          </template>
          <p v-if="!chatLoading && chatMessages.length === 0" class="chat-empty">暂无消息</p>
        </div>

        <div v-if="!chatReadonly" class="chat-panel__composer">
          <button
            type="button"
            class="chat-tool"
            :disabled="chatUploading"
            title="发送图片"
            @click="pickChatMedia('image')"
          >
            <el-icon :size="18"><Picture /></el-icon>
          </button>
          <button
            type="button"
            class="chat-tool"
            :disabled="chatUploading"
            title="发送视频"
            @click="pickChatMedia('video')"
          >
            <el-icon :size="18"><VideoCamera /></el-icon>
          </button>
          <input
            ref="imageInput"
            type="file"
            hidden
            :accept="CHAT_IMAGE_ACCEPT"
            @change="handleChatFile($event, 'image')"
          />
          <input
            ref="videoInput"
            type="file"
            hidden
            :accept="CHAT_VIDEO_ACCEPT"
            @change="handleChatFile($event, 'video')"
          />
          <el-input
            v-model="chatInput"
            type="textarea"
            :rows="2"
            maxlength="1000"
            resize="none"
            placeholder="回复客户，Enter 发送，Shift+Enter 换行"
            @keydown.enter.exact.prevent="sendChat"
          />
          <el-button type="primary" :loading="chatSending" @click="sendChat">发送</el-button>
        </div>
        <p v-else class="chat-panel__tip">该工单当前不在「处理中」，仅可查看历史记录。接单后即可与客户对话。</p>
      </div>
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

/* ---------------- 坐席会话面板 ---------------- */
.chat-panel {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 10px;
    border-bottom: 1px solid #eef1f6;
  }

  &__customer {
    font-size: 14px;
    font-weight: 600;
    color: #1f2d3d;

    small {
      margin-left: 6px;
      font-weight: 400;
      color: #98a2b3;
    }
  }

  &__body {
    flex: 1;
    margin-top: 8px;
    padding: 12px;
    overflow-y: auto;
    background: #f7f9fc;
    border-radius: 10px;
  }

  &__composer {
    display: flex;
    align-items: flex-end;
    gap: 8px;
    padding-top: 10px;
    border-top: 1px solid #eef1f6;
  }

  &__tip {
    padding-top: 10px;
    font-size: 12px;
    color: #98a2b3;
    border-top: 1px solid #eef1f6;
  }
}

.chat-sys {
  max-width: 88%;
  margin: 10px auto;
  padding: 5px 10px;
  font-size: 12px;
  line-height: 1.6;
  color: #667085;
  text-align: center;
  background: #eef1f6;
  border-radius: 10px;
}

.chat-msg {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-bottom: 12px;

  &__meta {
    display: flex;
    gap: 8px;
    margin-bottom: 3px;
    font-size: 11px;
    color: #98a2b3;
  }

  &__bubble {
    max-width: 78%;
    padding: 8px 11px;
    background: #fff;
    border-radius: 10px;
    box-shadow: 0 1px 4px rgba(16, 24, 40, 0.05);
  }

  &__text {
    font-size: 13px;
    line-height: 1.7;
    color: #1f2d3d;
    white-space: pre-wrap;
    word-break: break-word;
  }

  &__image {
    display: block;
    max-width: 220px;
    max-height: 220px;
    border-radius: 8px;
  }

  &__video {
    display: block;
    width: 240px;
    max-height: 240px;
    background: #000;
    border-radius: 8px;
  }

  &__size {
    display: block;
    margin-top: 4px;
    font-size: 11px;
    color: #98a2b3;
  }

  /* 坐席自己发的靠右,与微信式会话一致 */
  &--staff {
    align-items: flex-end;

    .chat-msg__meta {
      justify-content: flex-end;
    }

    .chat-msg__bubble {
      background: #eff8ff;
    }
  }

  &--assistant .chat-msg__bubble {
    background: #f4f6fb;
  }
}

.chat-tool {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: #667085;
  background: #f4f6fb;
  border: none;
  border-radius: 8px;
  cursor: pointer;

  &:hover {
    color: #1b5faa;
  }

  &:disabled {
    opacity: 0.4;
  }
}

.chat-empty {
  padding: 30px 0;
  font-size: 12px;
  color: #98a2b3;
  text-align: center;
}
</style>
