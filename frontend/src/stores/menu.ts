import { computed, ref, type Component } from 'vue';
import { defineStore } from 'pinia';
import type { Router } from 'vue-router';
import { getAdminMenusApi } from '@/api';
import type { MenuItem } from '@/types';

/** 所有后台页面组件,用于把后端返回的 component 字符串解析为组件 */
const viewModules = import.meta.glob('/src/views/**/*.vue');

/** 布局路由名称(动态子路由挂载在该路由下) */
export const LAYOUT_ROUTE_NAME = 'AdminLayout';

/**
 * 后台菜单状态
 * <p>负责:菜单树加载、菜单驱动动态路由注册、侧边栏渲染数据</p>
 */
export const useMenuStore = defineStore('adminMenu', () => {
  /** 菜单树(后端按当前角色过滤) */
  const menus = ref<MenuItem[]>([]);
  /** 是否已加载(路由守卫用,避免重复请求) */
  const loaded = ref(false);
  /** 已注册的动态路由名称,退出登录时移除 */
  const addedRouteNames = ref<string[]>([]);

  /** 第一个可访问的菜单路径(登录后默认落地页) */
  const firstPath = computed<string>(() => findFirstPath(menus.value) || '/404');

  /** 扁平化后的叶子菜单(面包屑 / 搜索用) */
  const flatMenus = computed<MenuItem[]>(() => flatten(menus.value));

  /**
   * 加载当前角色的菜单树
   */
  async function load(): Promise<MenuItem[]> {
    const data = await getAdminMenusApi();
    menus.value = data;
    loaded.value = true;
    return data;
  }

  /**
   * 注册动态路由
   * <p>菜单类型为 MENU 且配置了 component 的节点会注册为布局下的子路由</p>
   *
   * @param router 路由实例
   */
  function applyRoutes(router: Router): void {
    flatten(menus.value)
      .filter((item) => item.menuType === 'MENU' && item.path && item.component)
      .forEach((item) => {
        const loader = resolveComponent(item.component as string);
        if (!loader) {
          // 组件缺失时跳过,避免整棵路由注册失败
          console.warn(`[菜单] 未找到组件: ${item.component}`);
          return;
        }
        const routeName = `Menu_${item.id}`;
        router.addRoute(LAYOUT_ROUTE_NAME, {
          path: item.path as string,
          name: routeName,
          component: loader,
          meta: {
            title: item.menuName,
            icon: item.icon ?? undefined,
            menuId: item.id,
          },
        });
        addedRouteNames.value.push(routeName);
      });
  }

  /**
   * 重置:退出登录时移除动态路由并清空菜单
   */
  function reset(router?: Router): void {
    if (router) {
      addedRouteNames.value.forEach((name) => {
        if (router.hasRoute(name)) {
          router.removeRoute(name);
        }
      });
    }
    addedRouteNames.value = [];
    menus.value = [];
    loaded.value = false;
  }

  /**
   * 菜单 component 字符串解析为动态导入函数
   */
  function resolveComponent(component: string): (() => Promise<unknown>) | undefined {
    const key = `/src/views/${component.replace(/^\/+/, '')}.vue`;
    return viewModules[key] as (() => Promise<unknown>) | undefined;
  }

  return { menus, loaded, firstPath, flatMenus, load, applyRoutes, reset };
});

/** 递归查找第一个有路由地址的菜单 */
function findFirstPath(items: MenuItem[]): string | null {
  for (const item of items) {
    if (item.menuType === 'MENU' && item.path) {
      return item.path;
    }
    const childPath = findFirstPath(item.children ?? []);
    if (childPath) {
      return childPath;
    }
  }
  return null;
}

/** 菜单树扁平化 */
function flatten(items: MenuItem[]): MenuItem[] {
  return items.reduce<MenuItem[]>((acc, item) => {
    acc.push(item);
    if (item.children?.length) {
      acc.push(...flatten(item.children));
    }
    return acc;
  }, []);
}

/** 供类型推断使用的占位(避免未使用告警) */
export type MenuComponent = Component;
