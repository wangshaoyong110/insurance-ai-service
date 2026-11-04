/**
 * token 存取工具
 * <p>管理端与客户端使用两个独立的 storage key,互不覆盖,可在同一浏览器同时登录</p>
 */
const ADMIN_TOKEN_KEY = 'insurance_ai_admin_token';
const CUS_TOKEN_KEY = 'insurance_ai_cus_token';

/* ---------------- 管理端(业务操作人员) ---------------- */

/** 读取管理端 token */
export function getToken(): string {
  return localStorage.getItem(ADMIN_TOKEN_KEY) ?? '';
}

/** 写入管理端 token */
export function setToken(token: string): void {
  localStorage.setItem(ADMIN_TOKEN_KEY, token);
}

/** 清除管理端 token */
export function removeToken(): void {
  localStorage.removeItem(ADMIN_TOKEN_KEY);
}

/* ---------------- 客户端(咨询客户) ---------------- */

/** 读取客户端 token */
export function getCusToken(): string {
  return localStorage.getItem(CUS_TOKEN_KEY) ?? '';
}

/** 写入客户端 token */
export function setCusToken(token: string): void {
  localStorage.setItem(CUS_TOKEN_KEY, token);
}

/** 清除客户端 token */
export function removeCusToken(): void {
  localStorage.removeItem(CUS_TOKEN_KEY);
}
