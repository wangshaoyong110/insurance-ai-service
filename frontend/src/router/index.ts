import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { LAYOUT_ROUTE_NAME, useMenuStore } from '@/stores/menu';
import { getToken } from '@/utils/auth';

/**
 * 扩展路由 meta 类型
 */
declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题 */
    title?: string;
    /** 是否免登录访问 */
    public?: boolean;
    /** 菜单图标 */
    icon?: string;
    /** 对应菜单ID */
    menuId?: number;
  }
}

/**
 * 管理端路由
 * <p>除登录页与 404 外,业务页面全部由后端菜单动态注册(菜单驱动路由)</p>
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'AdminLogin',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '管理端登录', public: true },
  },
  {
    path: '/',
    name: LAYOUT_ROUTE_NAME,
    component: () => import('@/layouts/AdminLayout.vue'),
    // 子路由由菜单动态注入,见 stores/menu.ts
    children: [],
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', public: true },
  },
  {
    // 兜底路由:这里必须是"组件"而不是 redirect
    // 原因:重定向会在全局守卫执行前完成,守卫拿到的 to.fullPath 已经变成 /404,
    // 菜单动态路由注册后无法再回到用户最初访问的地址(表现为刷新子页面直接 404)。
    // 保留原始地址,守卫即可在菜单加载完成后按 to.fullPath 重新解析到动态路由。
    path: '/:pathMatch(.*)*',
    name: 'CatchAll',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在' },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
});

/**
 * 全局前置守卫
 * 1. 未登录访问受限页面 -> 登录页(带 redirect)
 * 2. 已登录但菜单未加载 -> 加载菜单并动态注册路由后重试当前地址
 * 3. 访问根路径 -> 落地到第一个有权限的菜单
 */
router.beforeEach(async (to) => {
  const appTitle = import.meta.env.VITE_APP_TITLE;
  document.title = to.meta.title ? `${to.meta.title} - ${appTitle}` : appTitle;

  const userStore = useUserStore();
  const menuStore = useMenuStore();
  const token = userStore.token || getToken();

  // 登录页:已登录则直接进入有权限的第一个菜单
  if (to.path === '/login') {
    if (!token) {
      return true;
    }
    if (!menuStore.loaded) {
      try {
        await menuStore.load();
        menuStore.applyRoutes(router);
      } catch {
        // 菜单加载失败视为登录态失效,停留在登录页
        userStore.reset();
        return true;
      }
    }
    return menuStore.firstPath;
  }

  // 免登录页面(404 等)
  if (to.meta.public) {
    return true;
  }

  if (!token) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }

  // 刷新页面后恢复管理员信息
  if (!userStore.userInfo) {
    try {
      await userStore.fetchUserInfo();
    } catch {
      userStore.reset();
      return { path: '/login', query: { redirect: to.fullPath } };
    }
  }

  // 菜单未加载:加载并注册动态路由后重新解析当前地址
  if (!menuStore.loaded) {
    try {
      await menuStore.load();
      menuStore.applyRoutes(router);
    } catch {
      userStore.reset();
      menuStore.reset(router);
      return { path: '/login' };
    }
    // 必须显式带上 query / hash:写成 { path: to.fullPath } 时,
    // vue-router 只把 fullPath 当路径处理,查询串会被丢掉(如 ?tab=claim 深链失效)
    return { path: to.path, query: to.query, hash: to.hash, replace: true };
  }

  // 根路径落地到第一个有权限的菜单
  if (to.path === '/') {
    return menuStore.firstPath;
  }

  return true;
});

export default router;
