import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { adminLoginApi, adminLogoutApi, getAdminInfoApi } from '@/api';
import { getToken, removeToken, setToken } from '@/utils/auth';
import type { LoginParams } from '@/types/api';
import type { AdminUserInfo } from '@/types';

/**
 * 管理端用户状态(业务操作人员)
 * <p>与客户端客户状态(cusUser)完全独立:token 分开存储,接口分开调用</p>
 */
export const useUserStore = defineStore(
  'adminUser',
  () => {
    /** 管理端登录令牌 */
    const token = ref<string>(getToken());
    /** 当前管理员信息 */
    const userInfo = ref<AdminUserInfo | null>(null);

    /** 是否已登录 */
    const isLogin = computed<boolean>(() => Boolean(token.value));
    /** 展示用名称 */
    const displayName = computed<string>(
      () => userInfo.value?.realName || userInfo.value?.username || '未登录',
    );
    /** 角色名称 */
    const roleName = computed<string>(() => userInfo.value?.roleName || '管理员');
    /** 是否超级管理员 */
    const isSuperAdmin = computed<boolean>(() => userInfo.value?.roleCode === 'SUPER_ADMIN');

    /**
     * 管理端登录
     */
    async function login(params: LoginParams): Promise<void> {
      const result = await adminLoginApi(params);
      token.value = result.token;
      userInfo.value = result.userInfo;
      setToken(result.token);
    }

    /**
     * 拉取当前管理员信息(刷新页面后恢复用户态)
     */
    async function fetchUserInfo(): Promise<AdminUserInfo> {
      const info = await getAdminInfoApi();
      userInfo.value = info;
      return info;
    }

    /**
     * 清空本地登录态
     */
    function reset(): void {
      token.value = '';
      userInfo.value = null;
      removeToken();
    }

    /**
     * 退出登录
     */
    async function logout(): Promise<void> {
      try {
        await adminLogoutApi();
      } catch {
        // 退出接口异常也要清理本地状态
      } finally {
        reset();
      }
    }

    return { token, userInfo, isLogin, displayName, roleName, isSuperAdmin, login, fetchUserInfo, logout, reset };
  },
  {
    persist: {
      key: 'insurance_ai_admin_user',
      storage: localStorage,
      // pinia-plugin-persistedstate v3 使用 paths 指定需要持久化的字段
      paths: ['token', 'userInfo'],
    },
  },
);
