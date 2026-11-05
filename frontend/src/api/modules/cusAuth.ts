import { cusGet, cusPost, cusPut } from '@/utils/cusRequest';
import type {
  ChangePasswordParams,
  CusLoginResult,
  LoginParams,
  RegisterParams,
  SmsCodeResult,
  SmsSendParams,
} from '@/types/api';
import type { CusUserInfo } from '@/types';

/**
 * 客户端认证接口(咨询客户)
 * 使用独立的 axios 实例与 token,与后台管理端隔离
 */

/** 客户登录(手机号 / 账号 + 密码) */
export function cusLoginApi(data: LoginParams): Promise<CusLoginResult> {
  return cusPost<CusLoginResult>('/auth/login', data);
}

/** 客户注册 */
export function cusRegisterApi(data: RegisterParams): Promise<number> {
  return cusPost<number>('/auth/register', data);
}

/** 获取短信验证码(注册等场景) */
export function cusSendSmsCodeApi(data: SmsSendParams): Promise<SmsCodeResult> {
  return cusPost<SmsCodeResult>('/auth/sms-code', data);
}

/** 客户退出登录 */
export function cusLogoutApi(): Promise<void> {
  return cusPost<void>('/auth/logout');
}

/** 当前客户信息 */
export function getCusInfoApi(): Promise<CusUserInfo> {
  return cusGet<CusUserInfo>('/auth/me');
}

/** 修改密码 */
export function changeCusPasswordApi(data: ChangePasswordParams): Promise<void> {
  return cusPut<void>('/auth/password', data);
}

/** 修改个人资料 */
export function updateCusProfileApi(nickname?: string, email?: string): Promise<CusUserInfo> {
  return cusPut<CusUserInfo>('/auth/profile', undefined, { params: { nickname, email } });
}
