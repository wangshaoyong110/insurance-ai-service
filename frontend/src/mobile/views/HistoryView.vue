<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { deleteSessionApi, getSessionsApi } from '@/api';
import { fromNow } from '@/utils';
import type { ChatSessionItem } from '@/types';

/**
 * 历史咨询:按最后消息时间倒序展示会话,支持回看与删除
 */
const router = useRouter();
const loading = ref(false);
const sessions = ref<ChatSessionItem[]>([]);

/**
 * 加载会话列表
 */
async function loadSessions(): Promise<void> {
  loading.value = true;
  try {
    sessions.value = await getSessionsApi();
  } finally {
    loading.value = false;
  }
}

/**
 * 进入会话详情
 */
function openSession(session: ChatSessionItem): void {
  void router.push({ path: '/chat', query: { sessionId: String(session.id) } });
}

/**
 * 删除会话
 */
async function handleDelete(session: ChatSessionItem, event: Event): Promise<void> {
  event.stopPropagation();
  try {
    await ElMessageBox.confirm(`确定删除「${session.title}」这条咨询记录吗?`, '提示', {
      type: 'warning',
      confirmButtonText: '删除',
    });
  } catch {
    return;
  }
  await deleteSessionApi(session.id);
  ElMessage.success('已删除');
  await loadSessions();
}

/**
 * 新建咨询
 */
function startNew(): void {
  void router.push('/chat');
}

onMounted(loadSessions);
</script>

<template>
  <div class="m-page history-page">
    <header class="m-header">
      <div class="m-header__title">
        历史咨询
        <span class="m-header__sub">共 {{ sessions.length }} 条记录</span>
      </div>
      <button type="button" class="m-header__action" title="新建咨询" @click="startNew">
        <el-icon><Plus /></el-icon>
      </button>
    </header>

    <div v-loading="loading" class="history-body">
      <div v-if="!loading && sessions.length === 0" class="empty">
        <el-icon :size="46"><ChatDotSquare /></el-icon>
        <p>还没有咨询记录</p>
        <button type="button" class="empty__btn" @click="startNew">去提问</button>
      </div>

      <ul v-else class="session-list">
        <li
          v-for="item in sessions"
          :key="item.id"
          class="session-item"
          @click="openSession(item)"
        >
          <div class="session-item__icon">
            <el-icon :size="18"><ChatLineRound /></el-icon>
          </div>
          <div class="session-item__main">
            <div class="session-item__head">
              <span class="session-item__title m-ellipsis">{{ item.title }}</span>
              <span class="session-item__time">{{ fromNow(item.lastMessageTime) }}</span>
            </div>
            <p class="session-item__desc m-ellipsis">{{ item.lastMessage || '暂无消息' }}</p>
            <div class="session-item__foot">
              <span>{{ item.messageCount }} 条消息</span>
              <button type="button" class="session-item__del" @click="handleDelete(item, $event)">
                删除
              </button>
            </div>
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped lang="scss">
.history-page {
  background: #f4f6fb;
}

.history-body {
  min-height: 60vh;
  padding: 12px 14px 20px;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 70px 0;
  color: #b3bccb;

  p {
    margin-top: 12px;
    font-size: 13px;
  }

  &__btn {
    margin-top: 18px;
    padding: 10px 26px;
    border: none;
    border-radius: 20px;
    color: #fff;
    font-size: 14px;
    background: linear-gradient(135deg, #1b5faa, #3f8ed0);
    box-shadow: 0 6px 16px rgba(27, 95, 170, 0.24);
  }
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.session-item {
  display: flex;
  gap: 10px;
  padding: 14px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 2px 10px rgba(16, 24, 40, 0.04);
  cursor: pointer;

  &__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 34px;
    border-radius: 10px;
    color: #1b5faa;
    background: #e8f0fb;
    flex-shrink: 0;
  }

  &__main {
    flex: 1;
    min-width: 0;
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
  }

  &__title {
    font-size: 14px;
    font-weight: 600;
    color: #1f2d3d;
  }

  &__time {
    font-size: 11px;
    color: #b3bccb;
    flex-shrink: 0;
  }

  &__desc {
    margin-top: 5px;
    font-size: 12px;
    color: #667085;
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 8px;
    font-size: 11px;
    color: #b3bccb;
  }

  &__del {
    padding: 2px 8px;
    font-size: 11px;
    color: #f04438;
    background: none;
    border: none;
    cursor: pointer;
  }
}
</style>
