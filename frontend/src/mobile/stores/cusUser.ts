import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { cusLoginApi, cusLogoutApi, getCusInfoApi } from '@/api';
import { getCusToken, removeCusToken, setCusToken } from '@/utils/auth';
import type { LoginParams } from '@/types/api';
import type { CusUserInfo } from '@/types';

/**
 * 客户端客户状态(手机端 H5 使用)
 * <p>与后台管理端用户体系完全独立,token 分开持久化</p>
 */
export const useCusUserStore = defineStore(
  'cusUser',
  () => {
    /** 客户端令牌 */
    const token = ref<string>(getCusToken());
    /** 客户信息 */
    const userInfo = ref<CusUserInfo | null>(null);

    /** 是否已登录 */
    const isLogin = computed<boolean>(() => Boolean(token.value));
    /** 展示名称 */
    const displayName = computed<string>(
      () => userInfo.value?.nickname || userInfo.value?.realName || '亲爱的客户',
    );
    /** 手机号脱敏展示 */
    const maskedPhone = computed<string>(() => {
      const phone = userInfo.value?.phone ?? '';
      return phone.length === 11 ? `${phone.slice(0, 3)}****${phone.slice(7)}` : phone;
    });

    /**
     * 客户登录
     */
    async function login(params: LoginParams): Promise<void> {
      const result = await cusLoginApi(params);
      token.value = result.token;
      userInfo.value = result.userInfo;
      setCusToken(result.token);
    }

    /**
     * 拉取客户信息
     */
    async function fetchUserInfo(): Promise<CusUserInfo> {
      const info = await getCusInfoApi();
      userInfo.value = info;
      return info;
    }

    /**
     * 更新本地客户信息(资料修改后同步)
     */
    function setUserInfo(info: CusUserInfo): void {
      userInfo.value = info;
    }

    /**
     * 清空登录态
     */
    function reset(): void {
      token.value = '';
      userInfo.value = null;
      removeCusToken();
    }

    /**
     * 退出登录
     */
    async function logout(): Promise<void> {
      try {
        await cusLogoutApi();
      } catch {
        // 接口异常也要清理本地状态
      } finally {
        reset();
      }
    }

    return {
      token,
      userInfo,
      isLogin,
      displayName,
      maskedPhone,
      login,
      fetchUserInfo,
      setUserInfo,
      logout,
      reset,
    };
  },
  {
    persist: {
      key: 'insurance_ai_cus_user',
      storage: localStorage,
      paths: ['token', 'userInfo'],
    },
  },
);
