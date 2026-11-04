/**
 * 接口入参 / 出参类型定义
 */
import type {
  AdminUserInfo,
  CusUserInfo,
  LoginLogItem,
  MenuItem,
  OperLogItem,
  PageQuery,
  RoleItem,
} from './index';

/** 后端统一响应结构 */
export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
  timestamp: number;
}

/** 登录参数(账号或手机号 + 密码),两端共用 */
export interface LoginParams {
  account: string;
  password: string;
}

/** 管理端登录返回 */
export interface AdminLoginResult {
  token: string;
  tokenPrefix: string;
  expiresIn: number;
  userInfo: AdminUserInfo;
}

/** 客户端登录返回 */
export interface CusLoginResult {
  token: string;
  tokenPrefix: string;
  expiresIn: number;
  userInfo: CusUserInfo;
}

/** 客户端注册参数 */
export interface RegisterParams {
  username?: string;
  password: string;
  confirmPassword: string;
  nickname?: string;
  realName?: string;
  phone: string;
  email?: string;
}

/** 修改密码参数 */
export interface ChangePasswordParams {
  oldPassword: string;
  newPassword: string;
}

/* ---------------- 管理端账号 ---------------- */

/** 管理员查询参数 */
export interface AdminUserQueryParams extends PageQuery {
  keyword?: string;
  roleId?: number;
  status?: number;
}

/** 管理员新增/编辑参数 */
export interface AdminUserSaveParams {
  id?: number;
  username: string;
  password?: string;
  realName?: string;
  phone: string;
  email?: string;
  avatar?: string;
  roleId: number;
  status?: number;
  remark?: string;
}

/* ---------------- 客户端客户 ---------------- */

/** 客户查询参数 */
export interface CusUserQueryParams extends PageQuery {
  keyword?: string;
  status?: number;
}

/* ---------------- 菜单 / 角色 ---------------- */

/** 菜单新增/编辑参数 */
export interface MenuSaveParams {
  id?: number;
  parentId: number;
  menuName: string;
  menuType: 'DIR' | 'MENU' | 'BUTTON';
  path?: string;
  component?: string;
  icon?: string;
  perms?: string;
  sort?: number;
  visible?: number;
  status?: number;
  remark?: string;
}

/** 角色新增/编辑参数 */
export interface RoleSaveParams {
  id?: number;
  roleCode: string;
  roleName: string;
  sort?: number;
  status?: number;
  remark?: string;
  menuIds?: number[];
}

/** 菜单树节点(管理端复用) */
export type MenuTreeNode = MenuItem;

/** 角色下拉项 */
export type RoleOption = Pick<RoleItem, 'id' | 'roleName' | 'roleCode'>;

/* ---------------- 智能客服 ---------------- */

/** 发送消息参数 */
export interface ChatSendParams {
  sessionId?: number | null;
  content: string;
}

/* ---------------- 知识库 ---------------- */

/** 知识库查询参数 */
export interface KbQueryParams extends PageQuery {
  keyword?: string;
  category?: string;
  status?: number;
}

/** 知识库新增/编辑参数 */
export interface KbSaveParams {
  id?: number;
  category: string;
  question: string;
  answer: string;
  keywords?: string;
  status?: number;
  remark?: string;
}

/* ---------------- 日志 ---------------- */

/** 日志查询参数 */
export interface LogQueryParams extends PageQuery {
  keyword?: string;
  status?: number;
  loginType?: string;
  startDate?: string;
  endDate?: string;
}

/** 登录日志分页 */
export type LoginLogPage = {
  records: LoginLogItem[];
  total: number;
  pageNum: number;
  pageSize: number;
};

/** 操作日志分页 */
export type OperLogPage = {
  records: OperLogItem[];
  total: number;
  pageNum: number;
  pageSize: number;
};

/* ---------------- 保单 / 理赔 ---------------- */

/** 保单查询参数 */
export interface PolicyQueryParams extends PageQuery {
  /** 保单号(模糊) */
  policyNo?: string;
  /** 客户关键字:姓名 / 手机号 */
  customer?: string;
  /** 险种 */
  category?: string;
  /** 状态 */
  status?: number;
}

/** 理赔查询参数 */
export interface ClaimQueryParams extends PageQuery {
  /** 理赔案号(模糊) */
  claimNo?: string;
  /** 保单号(模糊) */
  policyNo?: string;
  /** 客户关键字:姓名 / 手机号 */
  customer?: string;
  /** 理赔类型 */
  claimType?: string;
  /** 状态 */
  status?: number;
}

/* ---------------- 大模型配置 ---------------- */

/** 模型配置新增/编辑参数 */
export interface AiModelSaveParams {
  id?: number;
  /** 配置名称 */
  configName: string;
  /** 提供方:DASHSCOPE / OPENAI_COMPATIBLE */
  provider: string;
  /** 接口地址,留空使用厂商默认 */
  baseUrl?: string;
  /** API Key 明文,编辑时留空表示不修改 */
  apiKey?: string;
  /** 模型名称 */
  model: string;
  /** 采样温度 */
  temperature?: number;
  /** 最大 token 数 */
  maxTokens?: number;
  /** 读超时(毫秒) */
  timeoutMs?: number;
  /** 是否默认:1-是 0-否 */
  isDefault?: number;
  /** 状态:1-启用 0-停用 */
  status?: number;
  remark?: string;
}

/** 模型连通性测试参数 */
export interface AiModelTestParams {
  /** 传入已保存配置ID 时,apiKey 留空则使用库中已加密的 Key */
  id?: number;
  provider?: string;
  baseUrl?: string;
  apiKey?: string;
  model?: string;
}

