import { ref, type Ref } from 'vue';

export interface ListPageFetchParams {
  page: number;
  pageSize: number;
}

export interface ListPageFetchResult<T> {
  records?: T[];
  total?: number | string;
}

export interface UseListPageOptions<T> {
  /**
   * 拉取列表数据。筛选条件由页面自己的 ref 闭包携带，
   * composable 只负责页码、竞态守卫、加载/错误状态。
   */
  fetcher: (params: ListPageFetchParams) => Promise<ListPageFetchResult<T>>;
  /** 每页条数，默认 20 */
  pageSize?: number;
  /** 加载失败的兜底文案；不传则取 error.message，再兜底「加载失败」 */
  errorMessage?: string;
  /**
   * 失败时清空已加载数据（审核队列类页面语义：宁可空态也不展示过期数据）。
   * 默认 false：保留旧数据，由错误条提示重试。
   */
  clearOnError?: boolean;
  /** 立即拉取（默认 true），页面无需再写 onMounted(fetchList) */
  immediate?: boolean;
}

/**
 * 错误文案收口：服务端业务 message（中文）原样展示；
 * 纯 ASCII 的底层错误（Network Error / timeout of ... / HTTP 状态原文）
 * 不直接上屏，统一归为网络异常。
 */
function sanitizeError(e: any): string {
  const msg = (e && e.message) || '';
  if (!msg) return '';
  return /^[\x20-\x7E]*$/.test(msg) ? '网络异常，请稍后重试' : msg;
}

/**
 * 列表页通用状态机：页码 / 总数 / 加载 / 错误 / 竞态守卫。
 * 与 ZyListPage 组件配套，但也可单独使用。
 */
export function useListPage<T>(options: UseListPageOptions<T>) {
  const {
    fetcher,
    pageSize: initialPageSize = 20,
    errorMessage,
    clearOnError = false,
    immediate = true,
  } = options;

  const tableData = ref<T[]>([]) as Ref<T[]>;
  const loading = ref(false);
  const errorMsg = ref('');
  const total = ref(0);
  const currentPage = ref(1);
  const pageSize = ref(initialPageSize);

  /** 竞态守卫：只有最后一次请求能落地 */
  let listRequest = 0;

  async function fetchList() {
    const requestId = ++listRequest;
    loading.value = true;
    errorMsg.value = '';
    try {
      const data = await fetcher({ page: currentPage.value, pageSize: pageSize.value });
      if (requestId !== listRequest) return;
      tableData.value = data.records || [];
      total.value = Number(data.total ?? 0);
    } catch (e: any) {
      if (requestId !== listRequest) return;
      if (clearOnError) {
        tableData.value = [];
        total.value = 0;
      }
      errorMsg.value = errorMessage || sanitizeError(e) || '加载失败';
    } finally {
      if (requestId === listRequest) loading.value = false;
    }
  }

  /** 筛选条件变化：回第一页并刷新 */
  function handleFilterChange() {
    currentPage.value = 1;
    fetchList();
  }

  /** 关键字搜索：回第一页并刷新（与 handleFilterChange 同语义，保留两个名字便于页面表达意图） */
  function handleSearch() {
    handleFilterChange();
  }

  function handlePageChange(page: number) {
    currentPage.value = page;
    fetchList();
  }

  if (immediate) {
    fetchList();
  }

  return {
    tableData,
    loading,
    errorMsg,
    total,
    currentPage,
    pageSize,
    fetchList,
    handleFilterChange,
    handleSearch,
    handlePageChange,
  };
}
