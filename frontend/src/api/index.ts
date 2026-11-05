/**
 * 接口统一出口
 * 使用方式:import { adminLoginApi } from '@/api'
 *
 * 说明:管理端与客户端接口分模块维护,底层使用不同的 axios 实例与 token
 */
export * from './modules/adminAuth';
export * from './modules/user';
export * from './modules/customer';
export * from './modules/menu';
export * from './modules/role';
export * from './modules/kb';
export * from './modules/log';
export * from './modules/dashboard';
export * from './modules/file';
export * from './modules/cusAuth';
export * from './modules/cusChat';
export * from './modules/policy';
export * from './modules/aiModel';
export * from './modules/service';
export * from './modules/cusService';
