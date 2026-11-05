import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router';
import { useCusUserStore } from '@/mobile/stores/cusUser';
import { getCusToken } from '@/utils/auth';

/**
 * 扩展路由 meta:是否显示底部导航
 */
declare module 'vue-router' {
  interface RouteMeta {
    title?: string;
    /** 是否免登录 */
    public?: boolean;
    /** 是否显示底部 tabbar */
    tab?: boolean;
  }
}

/**
 * 手机端路由
 * 使用 hash 模式,便于静态部署时无需服务端重写规则(/mobile.html#/chat)
 */
const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/chat' },
  {
    path: '/login',
    name: 'MobileLogin',
    component: () => import('@/mobile/views/LoginView.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/chat',
    name: 'MobileChat',
    component: () => import('@/mobile/views/ChatView.vue'),
    meta: { title: '在线咨询', tab: true },
  },
  {
    path: '/history',
    name: 'MobileHistory',
    component: () => import('@/mobile/views/HistoryView.vue'),
    meta: { title: '历史咨询', tab: true },
  },
  {
    path: '/profile',
    name: 'MobileProfile',
    component: () => import('@/mobile/views/ProfileView.vue'),
    meta: { title: '我的', tab: true },
  },
  {
    // 工单进度从「我的」或咨询页进入，不占底部导航
    path: '/tickets',
    name: 'MobileTicket',
    component: () => import('@/mobile/views/TicketView.vue'),
    meta: { title: '我的工单' },
  },
  { path: '/:pathMatch(.*)*', redirect: '/chat' },
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
});

/**
 * 前置守卫:未登录跳转登录页
 */
router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 安鑫保险智能助手` : '安鑫保险智能助手';
  const userStore = useCusUserStore();
  const token = userStore.token || getCusToken();

  if (!to.meta.public && !token) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (to.path === '/login' && token) {
    return { path: '/chat' };
  }
  return true;
});

export default router;
