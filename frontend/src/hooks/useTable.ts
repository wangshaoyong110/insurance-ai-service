import { onMounted, reactive, ref, type Ref } from 'vue';
import type { PageQuery, PageResult } from '@/types';

/** useTable 配置项 */
export interface UseTableOptions<T, Q extends PageQuery> {
  /** 列表接口 */
  api: (params: Q) => Promise<PageResult<T>>;
  /** 查询条件默认值 */
  defaultQuery?: Partial<Q>;
  /** 每页条数 */
  pageSize?: number;
  /** 是否挂载后自动查询 */
  immediate?: boolean;
}

/** useTable 返回值 */
export interface UseTableReturn<T, Q extends PageQuery> {
  loading: Ref<boolean>;
  list: Ref<T[]>;
  total: Ref<number>;
  query: Ref<Q>;
  pagination: { pageNum: number; pageSize: number };
  loadData: () => Promise<void>;
  search: () => Promise<void>;
  reset: () => Promise<void>;
  handleCurrentChange: (page: number) => void;
  handleSizeChange: (size: number) => void;
}

/**
 * 表格逻辑 Hook:统一处理分页、查询、重置、加载状态
 *
 * @param options 配置项
 */
export function useTable<T, Q extends PageQuery = PageQuery>(
  options: UseTableOptions<T, Q>,
): UseTableReturn<T, Q> {
  const loading = ref(false);
  const list = ref([]) as Ref<T[]>;
  const total = ref(0);
  const query = ref({ ...(options.defaultQuery ?? {}) }) as Ref<Q>;
  const pagination = reactive({
    pageNum: 1,
    pageSize: options.pageSize ?? 10,
  });

  /**
   * 加载列表数据
   */
  async function loadData(): Promise<void> {
    loading.value = true;
    try {
      const result = await options.api({
        ...query.value,
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize,
      } as Q);
      list.value = result.records ?? [];
      total.value = result.total ?? 0;
    } catch {
      // 错误提示已由请求拦截器统一处理,这里只需保证列表状态可控
      list.value = [];
      total.value = 0;
    } finally {
      loading.value = false;
    }
  }

  /**
   * 条件查询:回到第一页
   */
  async function search(): Promise<void> {
    pagination.pageNum = 1;
    await loadData();
  }

  /**
   * 重置查询条件
   */
  async function reset(): Promise<void> {
    query.value = { ...(options.defaultQuery ?? {}) } as Q;
    pagination.pageNum = 1;
    await loadData();
  }

  function handleCurrentChange(page: number): void {
    pagination.pageNum = page;
    void loadData();
  }

  function handleSizeChange(size: number): void {
    pagination.pageSize = size;
    pagination.pageNum = 1;
    void loadData();
  }

  onMounted(() => {
    if (options.immediate !== false) {
      void loadData();
    }
  });

  return {
    loading,
    list,
    total,
    query,
    pagination,
    loadData,
    search,
    reset,
    handleCurrentChange,
    handleSizeChange,
  };
}
