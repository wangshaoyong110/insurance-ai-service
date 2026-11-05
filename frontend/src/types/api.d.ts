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
/** 登录参数(管理端 / 客户端共用) */
export interface LoginParams {
  account: string;
  password: string;
  /** 图形验证码 ID */
  captchaId: string;
  /** 图形验证码字符 */
  captchaCode: string;
}

/** 图形验证码 */
export interface CaptchaResult {
  captchaId: string;
  /** PNG Base64,不含 data: 前缀 */
  image: string;
  expireSeconds: number;
  /** 演示环境回显的验证码明文,生产恒为空 */
  debugCode?: string;
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
  /** 短信验证码(注册场景) */
  smsCode?: string;
  /** 图形验证码 ID:与获取短信验证码时用的是同一张 */
  captchaId: string;
  /** 图形验证码字符 */
  captchaCode: string;
}

/** 短信验证码发送参数 */
export interface SmsSendParams {
  phone: string;
  /** 使用场景:register / login / reset */
  scene: string;
  /** 图形验证码 ID,校验通过后才发短信 */
  captchaId: string;
  /** 图形验证码字符 */
  captchaCode: string;
}

/** 短信验证码发送结果 */
export interface SmsCodeResult {
  phone: string;
  scene: string;
  expireSeconds: number;
  resendSeconds: number;
  /** 演示环境回显的验证码,接入真实通道后恒为空 */
  debugCode?: string;
  tip: string;
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

/** 会话消息发送参数(文字 / 图片 / 视频) */
export interface ChatMessageSendParams {
  sessionId?: number | null;
  content?: string;
  /** text / image / video */
  msgType?: string;
  mediaUrl?: string;
  mediaName?: string;
  mediaSize?: number;
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


/* ---------------- 服务闭环 ---------------- */

/** 工单查询参数 */
export interface TicketQueryParams extends PageQuery {
  /** 工单号(模糊) */
  ticketNo?: string;
  /** 状态:0-待处理 1-处理中 2-已完成 3-已取消 */
  status?: number;
  /** 转人工原因 */
  reason?: string;
  /** 客户关键字:姓名 / 手机号 */
  customer?: string;
  /** 创建时间起 yyyy-MM-dd */
  startDate?: string;
  /** 创建时间止 yyyy-MM-dd */
  endDate?: string;
}

/** 工单创建参数(客户端转人工) */
export interface TicketCreateParams {
  sessionId?: number | null;
  question: string;
  category?: string;
  reason?: string;
  priority?: number;
}

/** 工单处理参数(管理端) */
export interface TicketHandleParams {
  /** 目标状态:1-处理中 2-已完成 3-已取消 */
  status: number;
  reply?: string;
  priority?: number;
}

/** 评价查询参数 */
export interface FeedbackQueryParams extends PageQuery {
  /** 1-有用 0-没用 */
  rating?: number;
  reason?: string;
  customer?: string;
  startDate?: string;
  endDate?: string;
}

/** 评价提交参数 */
export interface FeedbackParams {
  messageId: number;
  /** 1-有用 0-没用 */
  rating: number;
  reason?: string;
  content?: string;
}
