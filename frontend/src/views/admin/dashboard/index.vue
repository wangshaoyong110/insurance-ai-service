<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getStatisticsApi } from '@/api';
import { useUserStore } from '@/stores/user';
import type { DashboardStatistics } from '@/types';

/**
 * 管理端数据概览
 * 统计口径区分「管理端账号」与「客户端客户」两套用户体系
 */
const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);
const statistics = ref<DashboardStatistics>({
  sessionCount: 0,
  questionCount: 0,
  todaySession: 0,
  kbCount: 0,
  adminCount: 0,
  customerCount: 0,
  enabledCustomerCount: 0,
  todayCustomerCount: 0,
});

interface StatCard {
  label: string;
  value: number;
  icon: string;
  color: string;
  tip: string;
}

/** 指标卡片 */
const cards = computed<StatCard[]>(() => [
  {
    label: '累计咨询会话',
    value: statistics.value.sessionCount,
    icon: 'ChatDotRound',
    color: '#1b5faa',
    tip: '客户在手机端发起的会话总数',
  },
  {
    label: '累计提问量',
    value: statistics.value.questionCount,
    icon: 'ChatLineSquare',
    color: '#0f9d8f',
    tip: '客户提交的问题条数',
  },
  {
    label: '今日新增会话',
    value: statistics.value.todaySession,
    icon: 'TrendCharts',
    color: '#f79009',
    tip: '今日 0 点起产生的会话数',
  },
  {
    label: '知识库条数',
    value: statistics.value.kbCount,
    icon: 'Reading',
    color: '#7a5af8',
    tip: '启用中的标准问答数量',
  },
  {
    label: '注册客户数',
    value: statistics.value.customerCount,
    icon: 'User',
    color: '#12b76a',
    tip: `启用 ${statistics.value.enabledCustomerCount} 位,今日新增 ${statistics.value.todayCustomerCount} 位`,
  },
  {
    label: '后台员工数',
    value: statistics.value.adminCount,
    icon: 'Avatar',
    color: '#2e90fa',
    tip: '管理端业务操作人员账号数量',
  },
]);

/** 快捷入口 */
const shortcuts = [
  { label: '客户管理', desc: '查看客户与账号状态', path: '/service/customer', icon: 'User' },
  { label: '知识库维护', desc: '补充投保、退保等语料', path: '/kb', icon: 'Reading' },
  { label: '员工账号', desc: '新增坐席、分配角色', path: '/system/user', icon: 'Avatar' },
  { label: '菜单管理', desc: '维护后台导航与权限', path: '/system/menu', icon: 'Menu' },
  { label: '登录日志', desc: '按登录端追溯 IP 与浏览器', path: '/log/login', icon: 'Key' },
  { label: '操作日志', desc: '查看关键操作与耗时', path: '/log/oper', icon: 'Document' },
];

/**
 * 加载统计数据
 */
async function loadStatistics(): Promise<void> {
  loading.value = true;
  try {
    statistics.value = await getStatisticsApi();
  } finally {
    loading.value = false;
  }
}

onMounted(loadStatistics);
</script>

<template>
  <div v-loading="loading" class="page-container dashboard">
    <section class="dashboard__banner">
      <div>
        <h2>你好,{{ userStore.displayName }}</h2>
        <p>当前角色:{{ userStore.roleName }}。这里可以管理客户咨询、知识库与后台权限。</p>
      </div>
      <el-icon :size="56" class="dashboard__banner-icon"><DataLine /></el-icon>
    </section>

    <section class="dashboard__cards">
      <div v-for="item in cards" :key="item.label" class="stat-card">
        <div class="stat-card__icon" :style="{ background: `${item.color}1a`, color: item.color }">
          <el-icon :size="22"><component :is="item.icon" /></el-icon>
        </div>
        <div class="stat-card__content">
          <p class="stat-card__label">{{ item.label }}</p>
          <p class="stat-card__value">{{ item.value }}</p>
          <p class="stat-card__tip">{{ item.tip }}</p>
        </div>
      </div>
    </section>

    <section class="dashboard__panels">
      <div class="card panel">
        <h3 class="panel__title">快捷入口</h3>
        <div class="shortcut-grid">
          <button v-for="item in shortcuts" :key="item.path" type="button" class="shortcut" @click="router.push(item.path)">
            <el-icon :size="20"><component :is="item.icon" /></el-icon>
            <span class="shortcut__label">{{ item.label }}</span>
            <span class="shortcut__desc">{{ item.desc }}</span>
          </button>
        </div>
      </div>

      <div class="card panel">
        <h3 class="panel__title">系统说明</h3>
        <ul class="notice-list">
          <li>后台与管理端账号体系独立:员工使用 <code>sys_user</code>,客户使用 <code>cus_user</code>。</li>
          <li>导航菜单来自 <code>sys_menu</code>,按角色授权,新增菜单后刷新页面即可生效。</li>
          <li>登录日志会记录登录端(管理端 / 客户端)、IP、浏览器与操作系统。</li>
          <li>服务端日志按天滚动并压缩归档,可通过响应头 <code>X-Trace-Id</code> 追踪单次请求。</li>
        </ul>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.dashboard {
  &__banner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 26px 30px;
    margin-bottom: $spacing-lg;
    border-radius: $radius-lg;
    color: #fff;
    background: linear-gradient(120deg, #123c6b, #1b5faa 62%, #3f8ed0);

    h2 {
      font-size: 22px;
      font-weight: 600;
    }

    p {
      margin-top: 8px;
      font-size: 13px;
      color: rgba(255, 255, 255, 0.78);
    }

    &-icon {
      opacity: 0.32;
    }
  }

  &__cards {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: $spacing-lg;
    margin-bottom: $spacing-lg;
  }

  &__panels {
    display: grid;
    grid-template-columns: 1.4fr 1fr;
    gap: $spacing-lg;
  }
}

.stat-card {
  display: flex;
  gap: 14px;
  padding: 20px;
  background: #fff;
  border-radius: $radius-lg;
  box-shadow: $shadow-card;

  &__icon {
    width: 44px;
    height: 44px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  &__label {
    font-size: $font-size-sm;
    color: $color-text-secondary;
  }

  &__value {
    margin: 6px 0;
    font-size: 26px;
    font-weight: 700;
    color: $color-text-primary;
    line-height: 1.2;
  }

  &__tip {
    font-size: 12px;
    color: $color-text-secondary;
  }
}

.panel {
  padding: 22px;

  &__title {
    font-size: $font-size-lg;
    font-weight: 600;
    margin-bottom: $spacing-md;
  }
}

.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

.shortcut {
  display: grid;
  grid-template-columns: 22px 1fr;
  grid-template-rows: auto auto;
  gap: 4px 10px;
  align-items: center;
  padding: 14px 16px;
  border: 1px solid $color-border-light;
  border-radius: $radius-md;
  background: #fbfcfe;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    border-color: $color-primary;
    background: rgba(27, 95, 170, 0.04);
  }

  &__label {
    font-size: 14px;
    font-weight: 600;
    color: $color-text-primary;
  }

  &__desc {
    grid-column: 2;
    font-size: 12px;
    color: $color-text-secondary;
  }
}

.notice-list {
  display: flex;
  flex-direction: column;
  gap: 12px;

  li {
    position: relative;
    padding-left: 16px;
    font-size: 13px;
    line-height: 1.8;
    color: $color-text-regular;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 9px;
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: $color-primary;
    }

    code {
      padding: 1px 5px;
      border-radius: 4px;
      background: rgba(27, 95, 170, 0.08);
      color: $color-primary;
      font-size: 12px;
    }
  }
}

@media (max-width: 1200px) {
  .dashboard__panels {
    grid-template-columns: 1fr;
  }
}
</style>
