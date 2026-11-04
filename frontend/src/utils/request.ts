import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios';
import { ElLoading, ElMessage } from 'element-plus';
import type { ApiResult } from '@/types/api';
import { getToken, removeToken } from './auth';

/**
 * 业务状态码(与后端 ResultCode 对应)
 */
export const BIZ_CODE = {
  SUCCESS: 200,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  TOKEN_EXPIRED: 4002,
} as const;

/**
 * 扩展 axios 配置:支持 loading 控制、错误提示开关、重复请求取消
 * 说明:这里通过声明合并扩展类型,避免业务代码出现 any
 */
declare module 'axios' {
  export interface AxiosRequestConfig {
    /** 是否显示全屏 loading,默认 false */
    loading?: boolean;
    /** 是否自动弹出错误提示,默认 true */
    showError?: boolean;
    /** 是否取消重复请求,默认 true */
    cancelDuplicate?: boolean;
    /** 内部使用:请求唯一标识 */
    requestKey?: string;
  }
}

/** 业务异常(由响应拦截器抛出,便于调用方统一 catch) */
export class BizError extends Error {
  readonly code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = 'BizError';
    this.code = code;
  }
}

/** 待处理请求:key -> AbortController */
const pendingMap = new Map<string, AbortController>();

/** loading 计数器(多实例共享,避免并发请求重复关闭) */
let loadingCount = 0;
let loadingInstance: ReturnType<typeof ElLoading.service> | null = null;

function showLoading(): void {
  loadingCount += 1;
  if (loadingCount === 1) {
    loadingInstance = ElLoading.service({
      lock: true,
      text: '加载中...',
      background: 'rgba(255, 255, 255, 0.7)',
    });
  }
}

function hideLoading(): void {
  loadingCount = Math.max(0, loadingCount - 1);
  if (loadingCount === 0 && loadingInstance) {
    loadingInstance.close();
    loadingInstance = null;
  }
}

/**
 * 生成请求唯一标识:方法 + 地址 + 参数
 */
function buildRequestKey(config: InternalAxiosRequestConfig): string {
  const { method, url, params, data } = config;
  return [method, url, JSON.stringify(params ?? {}), JSON.stringify(data ?? {})].join('&');
}

/**
 * 加入待处理队列,并取消同一时刻的重复请求
 */
function addPending(config: InternalAxiosRequestConfig): void {
  if (config.cancelDuplicate === false) {
    return;
  }
  const key = buildRequestKey(config);
  config.requestKey = key;
  const previous = pendingMap.get(key);
  if (previous) {
    previous.abort('重复请求已取消');
    pendingMap.delete(key);
  }
  const controller = new AbortController();
  config.signal = controller.signal;
  pendingMap.set(key, controller);
}

/**
 * 请求完成后移出队列
 */
function removePending(config?: InternalAxiosRequestConfig): void {
  if (!config?.requestKey) {
    return;
  }
  pendingMap.delete(config.requestKey);
}

/** 创建 http 实例的配置 */
export interface HttpOptions {
  /** 获取 token 的方法(管理端 / 客户端各自独立) */
  tokenGetter: () => string;
  /** 登录失效时的处理:清理本地登录态 + 跳转对应登录页 */
  onUnauthorized: (message: string) => void;
}

/** http 实例能力 */
export interface HttpInstance {
  /** 原始 axios 实例 */
  service: AxiosInstance;
  /** 通用请求方法 */
  request: <T = unknown>(config: AxiosRequestConfig) => Promise<T>;
  get: <T = unknown>(url: string, params?: object, config?: AxiosRequestConfig) => Promise<T>;
  post: <T = unknown>(url: string, data?: object, config?: AxiosRequestConfig) => Promise<T>;
  put: <T = unknown>(url: string, data?: object, config?: AxiosRequestConfig) => Promise<T>;
  del: <T = unknown>(url: string, params?: object, config?: AxiosRequestConfig) => Promise<T>;
  upload: <T = unknown>(
    url: string,
    file: File | Blob,
    extra?: Record<string, string>,
    config?: AxiosRequestConfig,
  ) => Promise<T>;
}

/**
 * 创建带统一拦截器的 http 实例
 * <p>管理端与客户端分别创建,实现 token 隔离与登录页跳转隔离</p>
 */
export function createHttp(options: HttpOptions): HttpInstance {
  const service: AxiosInstance = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL,
    timeout: 15000,
    headers: {
      'Content-Type': 'application/json;charset=UTF-8',
    },
  });

  /**
   * 登录失效处理:清理 token 并交由调用方跳转
   */
  function handleUnauthorized(message: string): void {
    options.onUnauthorized(message);
  }

  // ---------------- 请求拦截器 ----------------
  service.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
      const token = options.tokenGetter();
      if (token) {
        // 自动注入 token
        config.headers.Authorization = `Bearer ${token}`;
      }
      addPending(config);
      if (config.loading) {
        showLoading();
      }
      return config;
    },
    (error: AxiosError) => {
      hideLoading();
      return Promise.reject(error);
    },
  );

  // ---------------- 响应拦截器 ----------------
  service.interceptors.response.use(
    (response: AxiosResponse) => {
      removePending(response.config);
      if (response.config.loading) {
        hideLoading();
      }
      // 文件流等非标准返回直接透传
      if (response.config.responseType === 'blob') {
        return response as unknown as AxiosResponse;
      }
      const body = response.data as ApiResult<unknown>;
      if (body.code === BIZ_CODE.SUCCESS) {
        // 统一解构为业务数据,交由 request() 取 data 字段
        return body as unknown as AxiosResponse;
      }
      // 登录态失效
      if (body.code === BIZ_CODE.UNAUTHORIZED || body.code === BIZ_CODE.TOKEN_EXPIRED) {
        handleUnauthorized(body.message);
        return Promise.reject(new BizError(body.code, body.message));
      }
      if (response.config.showError !== false) {
        ElMessage.error(body.message || '请求失败');
      }
      return Promise.reject(new BizError(body.code, body.message));
    },
    (error: AxiosError<ApiResult<unknown>>) => {
      removePending(error.config as InternalAxiosRequestConfig | undefined);
      if (error.config?.loading) {
        hideLoading();
      }
      // 重复请求被主动取消,无需提示
      if (axios.isCancel(error)) {
        return Promise.reject(error);
      }
      const status = error.response?.status;
      let message = '网络异常,请稍后重试';
      if (error.code === 'ECONNABORTED' || error.message.includes('timeout')) {
        message = '请求超时,请检查网络后重试';
      } else if (status === 401) {
        handleUnauthorized('登录状态已失效,请重新登录');
        return Promise.reject(error);
      } else if (status === 403) {
        message = '没有操作权限';
      } else if (status === 404) {
        message = '请求的接口不存在';
      } else if (status && status >= 500) {
        message = '服务器开小差了,请稍后再试';
      } else if (error.response?.data?.message) {
        message = error.response.data.message;
      }
      if (error.config?.showError !== false) {
        ElMessage.error(message);
      }
      return Promise.reject(error);
    },
  );

  /**
   * 通用请求方法:自动解构业务数据,保留 TS 类型推导
   */
  async function request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
    const body = await service.request<unknown, ApiResult<T>>(config);
    return body.data;
  }

  return {
    service,
    request,
    get: <T = unknown>(url: string, params?: object, config?: AxiosRequestConfig) =>
      request<T>({ ...config, url, method: 'get', params }),
    post: <T = unknown>(url: string, data?: object, config?: AxiosRequestConfig) =>
      request<T>({ ...config, url, method: 'post', data }),
    put: <T = unknown>(url: string, data?: object, config?: AxiosRequestConfig) =>
      request<T>({ ...config, url, method: 'put', data }),
    del: <T = unknown>(url: string, params?: object, config?: AxiosRequestConfig) =>
      request<T>({ ...config, url, method: 'delete', params }),
    upload: <T = unknown>(
      url: string,
      file: File | Blob,
      extra?: Record<string, string>,
      config?: AxiosRequestConfig,
    ) => {
      const formData = new FormData();
      formData.append('file', file);
      Object.entries(extra ?? {}).forEach(([key, value]) => formData.append(key, value));
      return request<T>({
        ...config,
        url,
        method: 'post',
        data: formData,
        headers: { 'Content-Type': 'multipart/form-data' },
      });
    },
  };
}

/**
 * 管理端 http 实例
 * <p>token 取管理端存储,登录失效跳转管理端登录页 /login</p>
 */
export const http = createHttp({
  tokenGetter: getToken,
  onUnauthorized: (message: string) => {
    removeToken();
    ElMessage.error(message || '登录状态已失效,请重新登录');
    const { pathname, search, hash } = window.location;
    if (pathname.startsWith('/login')) {
      return;
    }
    const redirect = encodeURIComponent(`${pathname}${search}${hash}`);
    window.location.href = `/login?redirect=${redirect}`;
  },
});

/** 管理端通用请求方法 */
export const request = http.request;
/** 管理端 GET */
export const get = http.get;
/** 管理端 POST */
export const post = http.post;
/** 管理端 PUT */
export const put = http.put;
/** 管理端 DELETE */
export const del = http.del;
/** 管理端文件上传 */
export const upload = http.upload;

export default http.service;
