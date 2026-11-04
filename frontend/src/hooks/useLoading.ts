import type { Ref } from 'vue';
import { ref } from 'vue';

/**
 * loading 状态管理
 * @param initial 初始状态
 */
export function useLoading(initial = false): {
  loading: Ref<boolean>;
  setLoading: (value: boolean) => void;
  withLoading: <T>(task: () => Promise<T>) => Promise<T>;
} {
  const loading = ref(initial);

  function setLoading(value: boolean): void {
    loading.value = value;
  }

  /**
   * 包裹异步任务,自动开启/关闭 loading(异常时同样关闭)
   */
  async function withLoading<T>(task: () => Promise<T>): Promise<T> {
    setLoading(true);
    try {
      return await task();
    } finally {
      setLoading(false);
    }
  }

  return { loading, setLoading, withLoading };
}
