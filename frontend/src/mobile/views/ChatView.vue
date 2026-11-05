<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import {
  createTicketApi,
  getCategoriesApi,
  getFeedbackReasonsApi,
  getMessagesApi,
  getTicketReasonsApi,
  sendMessageStreamApi,
  submitFeedbackApi,
} from '@/api';
import { useCusUserStore } from '@/mobile/stores/cusUser';
import { renderMarkdown } from '@/utils/markdown';
import type { CategoryItem, ChatMessageItem, OptionItem } from '@/types';

/**
 * 客户端在线咨询页
 * <p>支持快捷提问、连续多轮对话、历史会话回看；回复通过 SSE 流式逐字渲染</p>
 * <p>业务上补了两条兜底路径：对回答做「有用/没用」评价，以及答不了时一键转人工</p>
 */
const route = useRoute();
const router = useRouter();
const cusUserStore = useCusUserStore();

/** 当前会话ID(新会话为 null) */
const sessionId = ref<number | null>(null);
/** 消息列表 */
const messages = ref<ChatMessageItem[]>([]);
/** 输入内容 */
const input = ref('');
/** 发送中(含流式输出期间) */
const sending = ref(false);
/** 正在流式输出的占位气泡 ID(用于显示打字动画) */
const streamingPlaceholderId = ref<number | null>(null);
/** 加载历史中 */
const loadingHistory = ref(false);
/** 业务分类(快捷提问) */
const categories = ref<CategoryItem[]>([]);
/** 消息滚动容器 */
const scrollRef = ref<HTMLElement>();
/** 最后一轮回答命中的业务分类(转人工时带给坐席) */
const lastCategory = ref<string>('OTHER');

/** 差评原因 / 转人工原因选项 */
const feedbackReasons = ref<OptionItem[]>([]);
const ticketReasons = ref<OptionItem[]>([]);

/** 流式请求中断控制器(离开页面时断开,避免后端白跑) */
let streamController: AbortController | null = null;
/** 滚动节流句柄(逐字输出时每帧都滚会明显掉帧) */
let scrollTimer = 0;

/** 是否展示欢迎区(无消息且非加载中) */
const showWelcome = computed<boolean>(() => !loadingHistory.value && messages.value.length === 0);

/** 默认快捷问题(分类接口异常时兜底) */
const defaultQuestions = [
  '投保需要满足什么条件?',
  '退保能退多少钱?犹豫期是多久?',
  '如何变更受益人?',
  '减保怎么办理?',
];

/** 快捷问题列表 */
const quickQuestions = computed<string[]>(() => {
  const fromCategory = categories.value
    .map((item) => item.sample)
    .filter((item): item is string => Boolean(item));
  return fromCategory.length ? fromCategory.slice(0, 4) : defaultQuestions;
});

/**
 * 滚动到底部(节流,避免流式逐字输出时每帧都强制布局)
 */
async function scrollToBottom(): Promise<void> {
  await nextTick();
  const el = scrollRef.value;
  if (el) {
    el.scrollTop = el.scrollHeight;
  }
}

function scheduleScrollToBottom(): void {
  if (scrollTimer) {
    return;
  }
  scrollTimer = window.setTimeout(() => {
    scrollTimer = 0;
    void scrollToBottom();
  }, 80);
}

/**
 * 发送消息(流式)
 * <p>
 * 先乐观插入用户消息与空的 AI 占位气泡，模型每吐一段就往占位气泡追加，
 * 失败时整体回滚并回填输入框。这样用户 1 秒内就能看到第一个字，
 * 而不是对着一个静止的打字动画等十几秒。
 * </p>
 */
async function handleSend(): Promise<void> {
  const content = input.value.trim();
  if (!content || sending.value) {
    return;
  }
  sending.value = true;

  const localUserMessage: ChatMessageItem = {
    id: -Date.now(),
    sessionId: sessionId.value ?? 0,
    role: 'user',
    content,
    createTime: new Date().toISOString(),
  };
  messages.value.push(localUserMessage);

  const placeholderId = localUserMessage.id - 1;
  messages.value.push({
    id: placeholderId,
    sessionId: sessionId.value ?? 0,
    role: 'assistant',
    content: '',
    createTime: new Date().toISOString(),
  });
  const placeholderIndex = messages.value.length - 1;
  streamingPlaceholderId.value = placeholderId;

  input.value = '';
  await scrollToBottom();

  let failed = false;
  const controller = new AbortController();
  streamController = controller;

  try {
    await sendMessageStreamApi(
      { sessionId: sessionId.value, content },
      {
        onMeta: (meta) => {
          // 新会话由后端创建，这里接管其 ID，后续轮次才能续上上下文
          sessionId.value = meta.sessionId;
          if (meta.category) {
            lastCategory.value = meta.category;
          }
        },
        onDelta: (text) => {
          const item = messages.value[placeholderIndex];
          if (item) {
            item.content += text;
            scheduleScrollToBottom();
          }
        },
        onDone: (payload) => {
          // 只回填真实 messageId，不改 id：改 key 会让 v-for 重建节点，
          // 流式刚结束时容易出现滚动跳动。评价操作依赖 messageId。
          const item = messages.value[placeholderIndex];
          if (item && payload?.messageId) {
            item.messageId = payload.messageId;
          }
          streamingPlaceholderId.value = null;
          void scrollToBottom();
        },
        onError: (message) => {
          failed = true;
          ElMessage.error(message);
        },
      },
      controller.signal,
    );
  } catch {
    // 中断(离开页面)不提示；网络异常给一次提示
    if (!controller.signal.aborted) {
      failed = true;
      ElMessage.error('网络异常，请稍后重试');
    }
  } finally {
    streamController = null;
    streamingPlaceholderId.value = null;
    sending.value = false;
  }

  if (failed) {
    // 回滚乐观插入的两条消息，并把问题回填输入框便于重试
    messages.value = messages.value.filter(
      (item) => item.id !== placeholderId && item.id !== localUserMessage.id,
    );
    input.value = content;
  }
}

/**
 * 点击快捷问题直接发送
 */
async function handleQuickAsk(question: string): Promise<void> {
  input.value = question;
  await handleSend();
}

/**
 * 新建会话
 */
async function startNewSession(): Promise<void> {
  sessionId.value = null;
  messages.value = [];
  await router.replace({ path: '/chat' });
}

/* ---------------- 回答评价 ---------------- */

/**
 * 取消息的真实 ID
 * <p>历史消息的 id 就是数据库主键；流式回复的临时 id 为负数，真实 ID 在 done 事件里回填</p>
 */
function realMessageId(item: ChatMessageItem): number | null {
  if (item.messageId) {
    return item.messageId;
  }
  return item.id > 0 ? item.id : null;
}

/** 差评弹窗 */
const feedbackDialog = reactive({
  visible: false,
  messageId: 0,
  reason: '',
  content: '',
  submitting: false,
});

/**
 * 评价一条回答
 *
 * @param rating 1-有用 0-没用
 */
async function rateAnswer(item: ChatMessageItem, rating: number): Promise<void> {
  const messageId = realMessageId(item);
  if (!messageId) {
    ElMessage.warning('回复还未保存完成，请稍后再评价');
    return;
  }
  if (item.feedback === rating) {
    return;
  }
  if (rating === 1) {
    await submitFeedbackApi({ messageId, rating: 1 });
    item.feedback = 1;
    ElMessage.success('感谢反馈');
    return;
  }
  // 点踩必须先选原因，否则这条数据对改进没有价值
  feedbackDialog.messageId = messageId;
  feedbackDialog.reason = '';
  feedbackDialog.content = '';
  feedbackDialog.visible = true;
}

/** 提交差评 */
async function submitBadFeedback(): Promise<void> {
  if (!feedbackDialog.reason) {
    ElMessage.warning('请选择不满意的原因');
    return;
  }
  feedbackDialog.submitting = true;
  try {
    await submitFeedbackApi({
      messageId: feedbackDialog.messageId,
      rating: 0,
      reason: feedbackDialog.reason,
      content: feedbackDialog.content.trim() || undefined,
    });
    const target = messages.value.find((item) => realMessageId(item) === feedbackDialog.messageId);
    if (target) {
      target.feedback = 0;
    }
    feedbackDialog.visible = false;
    ElMessage.success('已收到，我们会持续改进');
  } finally {
    feedbackDialog.submitting = false;
  }
}

/* ---------------- 转人工 ---------------- */

/** 转人工弹窗 */
const ticketDialog = reactive({
  visible: false,
  question: '',
  reason: 'DISSATISFIED',
  submitting: false,
});

/** 最近一次客户提问，作为工单问题摘要的默认值 */
function lastUserQuestion(): string {
  const last = [...messages.value].reverse().find((item) => item.role === 'user');
  const text = last?.content ?? '';
  return text.length > 200 ? `${text.slice(0, 200)}...` : text;
}

function openTicket(): void {
  ticketDialog.question = lastUserQuestion();
  ticketDialog.reason = 'DISSATISFIED';
  ticketDialog.visible = true;
}

/** 提交工单 */
async function submitTicket(): Promise<void> {
  if (!ticketDialog.question.trim()) {
    ElMessage.warning('请简单描述需要人工协助的问题');
    return;
  }
  ticketDialog.submitting = true;
  try {
    const ticket = await createTicketApi({
      sessionId: sessionId.value,
      question: ticketDialog.question.trim(),
      category: lastCategory.value,
      reason: ticketDialog.reason,
    });
    ticketDialog.visible = false;
    // 在对话流里留一条本地提示：客户提交完立刻能看到工单号，心里有底
    messages.value.push({
      id: -Date.now(),
      sessionId: sessionId.value ?? 0,
      role: 'assistant',
      content: `已为你转接人工客服，工单号 ${ticket.ticketNo}。坐席会在工作时间内尽快联系你，也可以在「我的 - 我的工单」查看处理进度。`,
      createTime: new Date().toISOString(),
      notice: true,
    });
    await scrollToBottom();
  } finally {
    ticketDialog.submitting = false;
  }
}

/**
 * 回车发送,Ctrl+Enter 换行
 */
function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' && !event.shiftKey && !event.ctrlKey) {
    event.preventDefault();
    void handleSend();
  }
}

/**
 * 加载历史会话消息
 */
async function loadHistory(id: number): Promise<void> {
  loadingHistory.value = true;
  try {
    const page = await getMessagesApi(id);
    messages.value = page.records.filter((item) => String(item.role) !== 'system');
    sessionId.value = id;
    await scrollToBottom();
  } finally {
    loadingHistory.value = false;
  }
}

/** 监听地址栏 sessionId 变化(从历史页进入) */
watch(
  () => route.query.sessionId,
  async (value) => {
    const id = Number(value);
    if (id) {
      await loadHistory(id);
    }
  },
);

onMounted(async () => {
  // 恢复客户信息(刷新页面后 store 可能只有 token)
  if (cusUserStore.token && !cusUserStore.userInfo) {
    try {
      await cusUserStore.fetchUserInfo();
    } catch {
      // 忽略:请求拦截器已处理登录失效
    }
  }
  try {
    categories.value = await getCategoriesApi();
  } catch {
    categories.value = [];
  }
  // 评价与转人工的原因选项由后端字典下发，避免前后端各写一份
  try {
    [feedbackReasons.value, ticketReasons.value] = await Promise.all([
      getFeedbackReasonsApi(),
      getTicketReasonsApi(),
    ]);
  } catch {
    feedbackReasons.value = [];
    ticketReasons.value = [];
  }
  const id = Number(route.query.sessionId);
  if (id) {
    await loadHistory(id);
  }
});

onBeforeUnmount(() => {
  // 主动断开 SSE，避免用户离开后后端继续生成、白白消耗 token
  streamController?.abort();
  if (scrollTimer) {
    window.clearTimeout(scrollTimer);
  }
});
</script>

<template>
  <div class="chat-page">
    <!-- 顶部栏 -->
    <header class="m-header">
      <div class="chat-avatar">
        <el-icon :size="20"><Service /></el-icon>
      </div>
      <div class="m-header__title">
        安鑫保险智能助手
        <span class="m-header__sub">投保 · 保全 · 退保 · 减保 在线咨询</span>
      </div>
      <button type="button" class="m-header__action" title="新建会话" @click="startNewSession">
        <el-icon><Plus /></el-icon>
      </button>
      <button type="button" class="m-header__action" title="历史咨询" @click="router.push('/history')">
        <el-icon><Clock /></el-icon>
      </button>
    </header>

    <!-- 消息区 -->
    <main ref="scrollRef" v-loading="loadingHistory" class="chat-body">
      <!-- 欢迎区 -->
      <section v-if="showWelcome" class="welcome">
        <div class="welcome__card">
          <p class="welcome__hello">你好,{{ cusUserStore.displayName }} 👋</p>
          <p class="welcome__desc">
            我是安鑫保险智能助手,可以为你解答投保条件、保全变更、退保损失、减保规则等问题。
          </p>
        </div>

        <p class="welcome__label">你可以这样问</p>
        <div class="welcome__questions">
          <button
            v-for="item in quickQuestions"
            :key="item"
            type="button"
            class="question-chip"
            @click="handleQuickAsk(item)"
          >
            <el-icon><ChatLineRound /></el-icon>
            <span>{{ item }}</span>
          </button>
        </div>

        <p class="welcome__privacy">
          <el-icon><Lock /></el-icon>
          请勿在对话中提供身份证号、银行卡号等敏感信息,我们将严格保护你的隐私
        </p>
      </section>

      <!-- 消息列表 -->
      <div v-else class="message-list">
        <div v-for="item in messages" :key="item.id" class="message" :class="`message--${item.role}`">
          <div class="message__avatar">
            <el-icon v-if="item.role === 'assistant'"><Service /></el-icon>
            <el-icon v-else><User /></el-icon>
          </div>
          <div class="message__bubble" :class="{ 'message__bubble--typing': item.id === streamingPlaceholderId && !item.content }">
            <!-- 首字到达前显示打字动画；一旦有内容就切到正文，实现逐字出现 -->
            <div v-if="item.id === streamingPlaceholderId && !item.content" class="typing-dots">
              <span></span><span></span><span></span>
            </div>
            <template v-else>
              <!-- 客户输入按纯文本展示；AI 回答渲染轻量 Markdown，否则 **加粗** 会带一串星号 -->
              <p v-if="item.role === 'user'" class="message__content">{{ item.content }}</p>
              <div
                v-else
                class="message__content message__content--md"
                v-html="renderMarkdown(item.content)"
              ></div>

              <!-- 回答评价：AI 回复生成完成后才出现，转人工提示等本地消息不参与评价 -->
              <div
                v-if="
                  item.role === 'assistant' &&
                  !item.notice &&
                  item.content &&
                  item.id !== streamingPlaceholderId
                "
                class="message__actions"
              >
                <template v-if="item.feedback === undefined">
                  <span class="message__ask">这个回答有用吗？</span>
                  <button type="button" class="rate-btn" @click="rateAnswer(item, 1)">
                    <el-icon><CaretTop /></el-icon>有用
                  </button>
                  <button type="button" class="rate-btn" @click="rateAnswer(item, 0)">
                    <el-icon><CaretBottom /></el-icon>没用
                  </button>
                </template>
                <span v-else class="message__rated">
                  {{ item.feedback === 1 ? '已反馈：有用' : '已反馈：没用，我们会改进' }}
                </span>
              </div>
            </template>
          </div>
        </div>
      </div>
    </main>

    <!-- 转人工入口：AI 答不了时给客户一条明确的出路 -->
    <div class="service-bar">
      <span class="service-bar__text">
        <el-icon><Headset /></el-icon>
        问题还没解决？
      </span>
      <button type="button" class="service-bar__btn" @click="openTicket">转人工客服</button>
      <button type="button" class="service-bar__link" @click="router.push('/tickets')">
        我的工单
      </button>
    </div>

    <!-- 输入区 -->
    <footer class="chat-composer">
      <textarea
        v-model="input"
        class="chat-composer__input"
        rows="1"
        maxlength="500"
        placeholder="请输入你的问题,例如:退保能退多少钱?"
        @keydown="handleKeydown"
      />
      <button
        type="button"
        class="chat-composer__send"
        :disabled="sending || !input.trim()"
        @click="handleSend"
      >
        <el-icon :size="18"><Promotion /></el-icon>
      </button>
    </footer>

    <!-- 差评反馈 -->
    <el-dialog v-model="feedbackDialog.visible" title="哪里不满意？" width="86%" align-center>
      <div class="feedback-reasons">
        <button
          v-for="item in feedbackReasons"
          :key="item.code"
          type="button"
          class="reason-chip"
          :class="{ 'is-active': feedbackDialog.reason === item.code }"
          @click="feedbackDialog.reason = item.code"
        >
          {{ item.name }}
        </button>
      </div>
      <el-input
        v-model="feedbackDialog.content"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        placeholder="补充说明（选填），例如：保额说错了"
      />
      <template #footer>
        <el-button @click="feedbackDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="feedbackDialog.submitting" @click="submitBadFeedback">
          提交
        </el-button>
      </template>
    </el-dialog>

    <!-- 转人工 -->
    <el-dialog v-model="ticketDialog.visible" title="转人工客服" width="86%" align-center>
      <p class="ticket-tip">提交后坐席会在工作时间内联系你，紧急业务建议直接拨打客服热线。</p>
      <el-input
        v-model="ticketDialog.question"
        type="textarea"
        :rows="3"
        maxlength="500"
        show-word-limit
        placeholder="简单描述需要人工协助的问题"
      />
      <p class="ticket-label">转人工原因</p>
      <div class="feedback-reasons">
        <button
          v-for="item in ticketReasons"
          :key="item.code"
          type="button"
          class="reason-chip"
          :class="{ 'is-active': ticketDialog.reason === item.code }"
          @click="ticketDialog.reason = item.code"
        >
          {{ item.name }}
        </button>
      </div>
      <template #footer>
        <el-button @click="ticketDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="ticketDialog.submitting" @click="submitTicket">
          提交工单
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  // 为底部 tabbar 预留空间(含 iPhone 安全区)
  padding-bottom: calc(58px + env(safe-area-inset-bottom, 0px));
  background: #f4f6fb;
  box-sizing: border-box;
}

.chat-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.18);
}

/* ---------------- 欢迎区 ---------------- */
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px 14px 6px;
  -webkit-overflow-scrolling: touch;
}

.welcome {
  &__card {
    padding: 18px;
    border-radius: 16px;
    background: linear-gradient(135deg, #ffffff, #eef5ff);
    box-shadow: 0 4px 14px rgba(27, 95, 170, 0.08);
  }

  &__hello {
    font-size: 17px;
    font-weight: 600;
    color: #1f2d3d;
  }

  &__desc {
    margin-top: 8px;
    font-size: 13px;
    line-height: 1.8;
    color: #475467;
  }

  &__label {
    margin: 20px 4px 10px;
    font-size: 12px;
    color: #98a2b3;
  }

  &__questions {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  &__privacy {
    display: flex;
    align-items: flex-start;
    gap: 6px;
    margin: 22px 4px 0;
    font-size: 11px;
    line-height: 1.7;
    color: #98a2b3;
  }
}

.question-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 13px 14px;
  text-align: left;
  font-size: 13px;
  color: #1b5faa;
  background: #fff;
  border: 1px solid #e4ecf7;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;

  &:active {
    background: #eef5ff;
    transform: scale(0.99);
  }
}

/* ---------------- 消息 ---------------- */
.message-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-bottom: 8px;
}

.message {
  display: flex;
  gap: 8px;
  align-items: flex-start;

  &__avatar {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 30px;
    height: 30px;
    border-radius: 9px;
    background: #e8f0fb;
    color: #1b5faa;
    flex-shrink: 0;
  }

  &__bubble {
    max-width: 78%;
    padding: 11px 13px;
    border-radius: 14px;
    background: #fff;
    box-shadow: 0 2px 8px rgba(16, 24, 40, 0.05);
  }

  &__content {
    font-size: 14px;
    line-height: 1.75;
    color: #1f2d3d;
    white-space: pre-wrap;
    word-break: break-word;
  }

  /* AI 回答渲染后的 Markdown：v-html 内容需 :deep 才能命中，且不能再保留 pre-wrap（会多出空行） */
  &__content--md {
    white-space: normal;

    :deep(p) {
      margin: 0 0 8px;
    }

    :deep(p:last-child) {
      margin-bottom: 0;
    }

    :deep(ul),
    :deep(ol) {
      margin: 4px 0 8px;
      padding-left: 18px;
    }

    /* 全局 reset 把列表样式清掉了，回答里的编号/圆点必须显式加回来 */
    :deep(ul) {
      list-style: disc;
    }

    :deep(ol) {
      list-style: decimal;
    }

    :deep(li) {
      margin: 2px 0;
      list-style: inherit;
      list-style-position: outside;
    }

    :deep(strong) {
      font-weight: 600;
    }

    :deep(code) {
      padding: 1px 4px;
      font-size: 12px;
      background: rgba(27, 95, 170, 0.08);
      border-radius: 4px;
    }

    :deep(h3),
    :deep(h4),
    :deep(h5) {
      margin: 10px 0 6px;
      font-size: 14px;
      font-weight: 600;
    }

    :deep(hr) {
      margin: 8px 0;
      border: none;
      border-top: 1px dashed #e4e7ec;
    }
  }

  /* 用户消息靠右 */
  &--user {
    flex-direction: row-reverse;

    .message__avatar {
      background: #dbeafe;
      color: #1b5faa;
    }

    .message__bubble {
      background: linear-gradient(135deg, #1b5faa, #3f8ed0);
      box-shadow: 0 6px 16px rgba(27, 95, 170, 0.24);
    }

    .message__content {
      color: #fff;
    }
  }

  &__bubble--typing {
    padding: 15px 16px;
  }

  /* 回答下方的评价操作 */
  &__actions {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: 9px;
    padding-top: 8px;
    border-top: 1px dashed #eef1f6;
  }

  &__ask {
    font-size: 12px;
    color: #98a2b3;
  }

  &__rated {
    font-size: 12px;
    color: #98a2b3;
  }
}

/* 评价按钮 */
.rate-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 8px;
  font-size: 12px;
  color: #475467;
  background: #f4f6fb;
  border: 1px solid transparent;
  border-radius: 20px;
  cursor: pointer;

  &:active {
    color: #1b5faa;
    border-color: rgba(27, 95, 170, 0.35);
    background: #eef5ff;
  }
}

/* 首字到达前的等待动画 */
.typing-dots {
  display: flex;
  align-items: center;
  gap: 5px;

  span {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #b3c7e0;
    animation: typing 1.2s infinite ease-in-out;

    &:nth-child(2) {
      animation-delay: 0.15s;
    }

    &:nth-child(3) {
      animation-delay: 0.3s;
    }
  }
}

@keyframes typing {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.5;
  }
  30% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

/* ---------------- 转人工入口 ---------------- */
.service-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 14px;
  background: #fff;
  border-top: 1px solid #eef1f6;

  &__text {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: #98a2b3;
    flex: 1;
  }

  &__btn {
    padding: 6px 12px;
    font-size: 12px;
    font-weight: 500;
    color: #1b5faa;
    background: #eef5ff;
    border: 1px solid rgba(27, 95, 170, 0.2);
    border-radius: 20px;
    cursor: pointer;

    &:active {
      background: #dbeafe;
    }
  }

  &__link {
    padding: 0;
    font-size: 12px;
    color: #667085;
    background: none;
    border: none;
    text-decoration: underline;
    cursor: pointer;
  }
}

/* ---------------- 弹窗 ---------------- */
.feedback-reasons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.reason-chip {
  padding: 7px 12px;
  font-size: 13px;
  color: #475467;
  background: #f4f6fb;
  border: 1px solid transparent;
  border-radius: 20px;
  cursor: pointer;

  &.is-active {
    color: #1b5faa;
    background: #eef5ff;
    border-color: rgba(27, 95, 170, 0.4);
  }
}

.ticket-tip {
  margin-bottom: 12px;
  font-size: 12px;
  line-height: 1.7;
  color: #98a2b3;
}

.ticket-label {
  margin: 14px 0 8px;
  font-size: 12px;
  color: #667085;
}

/* ---------------- 输入区 ---------------- */
.chat-composer {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 10px 12px;
  background: #fff;
  border-top: 1px solid #eef1f6;

  &__input {
    flex: 1;
    max-height: 96px;
    min-height: 40px;
    padding: 10px 12px;
    font-size: 14px;
    line-height: 1.5;
    color: #1f2d3d;
    background: #f4f6fb;
    border: 1px solid transparent;
    border-radius: 12px;
    outline: none;
    resize: none;
    font-family: inherit;

    &:focus {
      border-color: rgba(27, 95, 170, 0.35);
      background: #fff;
    }
  }

  &__send {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 42px;
    height: 42px;
    border: none;
    border-radius: 12px;
    color: #fff;
    background: linear-gradient(135deg, #1b5faa, #3f8ed0);
    box-shadow: 0 6px 14px rgba(27, 95, 170, 0.26);
    cursor: pointer;
    flex-shrink: 0;

    &:disabled {
      opacity: 0.45;
      box-shadow: none;
    }
  }
}
</style>
