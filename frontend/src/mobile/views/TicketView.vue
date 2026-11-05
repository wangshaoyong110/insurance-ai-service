<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { cancelTicketApi, getMyTicketsApi } from '@/api';
import type { TicketItem } from '@/types';

/**
 * 我的工单
 * <p>客户在这里看到自己提交的转人工请求走到哪一步、坐席回复了什么</p>
 */
const router = useRouter();
const loading = ref(false);
const tickets = ref<TicketItem[]>([]);

const statusText: Record<number, string> = {
  0: '待处理',
  1: '处理中',
  2: '已完成',
  3: '已取消',
};

const reasonText: Record<string, string> = {
  DISSATISFIED: '回答不满意',
  COMPLAINT: '投诉建议',
  COMPLEX: '业务复杂',
  OTHER: '其他',
};

/** 进行中的单子排在前面 */
const sorted = computed<TicketItem[]>(() =>
  [...tickets.value].sort((a, b) => {
    const weight = (item: TicketItem): number => (item.status < 2 ? 0 : 1);
    return weight(a) - weight(b);
  }),
);

function statusClass(status: number): string {
  return ['is-pending', 'is-processing', 'is-done', 'is-canceled'][status] ?? 'is-done';
}

async function loadTickets(): Promise<void> {
  loading.value = true;
  try {
    tickets.value = await getMyTicketsApi();
  } finally {
    loading.value = false;
  }
}

async function cancel(ticket: TicketItem): Promise<void> {
  try {
    await ElMessageBox.confirm('取消后需要重新提交，确定取消吗？', '取消工单', { type: 'warning' });
  } catch {
    return;
  }
  await cancelTicketApi(ticket.id);
  ElMessage.success('工单已取消');
  await loadTickets();
}

onMounted(loadTickets);
</script>

<template>
  <div class="m-page ticket-page">
    <header class="ticket-header">
      <button type="button" class="ticket-header__back" @click="router.back()">
        <el-icon><ArrowLeft /></el-icon>
      </button>
      <span class="ticket-header__title">我的工单</span>
      <span class="ticket-header__count">{{ tickets.length }} 张</span>
    </header>

    <main v-loading="loading" class="ticket-body">
      <div v-if="!loading && tickets.length === 0" class="empty">
        <el-icon :size="38"><Tickets /></el-icon>
        <p>还没有提交过工单</p>
        <span>在咨询页点「转人工客服」即可提交</span>
      </div>

      <article v-for="item in sorted" :key="item.id" class="ticket-card">
        <div class="ticket-card__head">
          <span class="ticket-card__no">{{ item.ticketNo }}</span>
          <span class="ticket-card__status" :class="statusClass(item.status)">
            {{ statusText[item.status] }}
          </span>
        </div>

        <p class="ticket-card__question">{{ item.question }}</p>

        <div class="ticket-card__meta">
          <span>{{ reasonText[item.reason] || item.reason }}</span>
          <span>{{ item.createTime }}</span>
        </div>

        <!-- 坐席回复：客户最关心的一段，结单后突出展示 -->
        <div v-if="item.reply" class="ticket-card__reply">
          <p class="ticket-card__reply-label">
            <el-icon><Service /></el-icon>
            坐席回复
          </p>
          <p class="ticket-card__reply-text">{{ item.reply }}</p>
          <p v-if="item.handlerName" class="ticket-card__handler">
            —— {{ item.handlerName }}<template v-if="item.finishTime"> · {{ item.finishTime }}</template>
          </p>
        </div>
        <p v-else-if="item.status === 1" class="ticket-card__waiting">
          <el-icon><Loading /></el-icon>
          {{ item.handlerName || '坐席' }} 正在处理，回复会出现在原咨询会话里
        </p>

        <div v-if="item.sessionId || item.status === 0" class="ticket-card__actions">
          <button
            v-if="item.sessionId"
            type="button"
            class="chat-btn"
            @click="router.push(`/chat?sessionId=${item.sessionId}`)"
          >
            进入会话
          </button>
          <button v-if="item.status === 0" type="button" class="cancel-btn" @click="cancel(item)">
            取消工单
          </button>
        </div>
      </article>
    </main>
  </div>
</template>

<style scoped lang="scss">
.ticket-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: #f4f6fb;
  // 无底部导航，只留安全区
  padding-bottom: env(safe-area-inset-bottom, 0px);
}

.ticket-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  color: #fff;
  background: linear-gradient(135deg, #1b5faa, #3f8ed0);

  &__back {
    display: flex;
    align-items: center;
    padding: 4px;
    color: #fff;
    background: none;
    border: none;
    cursor: pointer;
  }

  &__title {
    flex: 1;
    font-size: 16px;
    font-weight: 600;
  }

  &__count {
    font-size: 12px;
    color: rgba(255, 255, 255, 0.78);
  }
}

.ticket-body {
  flex: 1;
  padding: 14px;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 70px 0;
  color: #98a2b3;

  p {
    margin-top: 8px;
    font-size: 14px;
    color: #475467;
  }

  span {
    font-size: 12px;
  }
}

.ticket-card {
  margin-bottom: 12px;
  padding: 14px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 2px 10px rgba(16, 24, 40, 0.05);

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__no {
    font-size: 13px;
    font-weight: 600;
    color: #1f2d3d;
    letter-spacing: 0.2px;
  }

  &__status {
    padding: 3px 9px;
    font-size: 11px;
    border-radius: 20px;

    &.is-pending {
      color: #b54708;
      background: #fef0c7;
    }

    &.is-processing {
      color: #175cd3;
      background: #eff8ff;
    }

    &.is-done {
      color: #027a48;
      background: #ecfdf3;
    }

    &.is-canceled {
      color: #667085;
      background: #f2f4f7;
    }
  }

  &__question {
    margin: 10px 0 8px;
    font-size: 14px;
    line-height: 1.7;
    color: #1f2d3d;
    word-break: break-word;
  }

  &__meta {
    display: flex;
    justify-content: space-between;
    font-size: 11px;
    color: #98a2b3;
  }

  &__reply {
    margin-top: 10px;
    padding: 10px 12px;
    background: #f0f7ff;
    border-radius: 10px;
  }

  &__reply-label {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    font-weight: 600;
    color: #1b5faa;
  }

  &__reply-text {
    margin-top: 6px;
    font-size: 13px;
    line-height: 1.75;
    color: #1f2d3d;
    white-space: pre-wrap;
  }

  &__handler {
    margin-top: 6px;
    font-size: 11px;
    color: #98a2b3;
    text-align: right;
  }

  &__waiting {
    display: flex;
    align-items: center;
    gap: 5px;
    margin-top: 10px;
    font-size: 12px;
    color: #b54708;
  }

  &__actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 12px;
  }
}

.chat-btn {
  padding: 6px 14px;
  font-size: 12px;
  color: #fff;
  background: linear-gradient(135deg, #1b5faa, #3f8ed0);
  border: none;
  border-radius: 18px;
  cursor: pointer;
}

.cancel-btn {
  padding: 6px 14px;
  font-size: 12px;
  color: #667085;
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 18px;
  cursor: pointer;
}
</style>
