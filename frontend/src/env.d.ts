/// <reference types="vite/client" />

/**
 * 环境变量类型声明,与 .env.* 文件一一对应
 */
interface ImportMetaEnv {
  /** 应用标题 */
  readonly VITE_APP_TITLE: string;
  /** 应用简称 */
  readonly VITE_APP_SHORT_NAME: string;
  /** 当前环境 */
  readonly VITE_APP_ENV: 'development' | 'production';
  /** 接口基础地址 */
  readonly VITE_API_BASE_URL: string;
  /** 本地代理目标(仅开发环境使用) */
  readonly VITE_PROXY_TARGET?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
