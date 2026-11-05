import { cusGet, cusPost, cusPut } from '@/utils/cusRequest';
import type {
  CaptchaResult,
  CertificateTypeOption,
  ChangePasswordParams,
  CusLoginResult,
  CusProfileUpdateParams,
  LoginParams,
  RealNameAuthParams,
  RegisterParams,
  SmsCodeResult,
  SmsSendParams,
} from '@/types/api';
import type { CusUserInfo } from '@/types';

/**
 * 客户端认证接口(咨询客户)
 * 使用独立的 axios 实例与 token,与后台管理端隔离
 */

/** 获取图形验证码(登录 / 注册场景各一张) */
export function cusCaptchaApi(scene: 'login' | 'register'): Promise<CaptchaResult> {
  return cusGet<CaptchaResult>('/auth/captcha', { scene });
}

/** 客户登录(手机号 / 账号 + 密码 + 图形验证码) */
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

/** 修改个人资料(只传需要改的字段) */
export function updateCusProfileApi(data: CusProfileUpdateParams): Promise<CusUserInfo> {
  return cusPut<CusUserInfo>('/auth/profile', data);
}

/** 实名认证(姓名 + 证件类型 + 证件号) */
export function realNameAuthApi(data: RealNameAuthParams): Promise<CusUserInfo> {
  return cusPost<CusUserInfo>('/auth/real-name', data);
}

/** 证件类型字典 */
export function getCertTypesApi(): Promise<CertificateTypeOption[]> {
  return cusGet<CertificateTypeOption[]>('/auth/cert-types');
}
