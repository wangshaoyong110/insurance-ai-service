/**
 * 业务通用类型定义
 */

/** 统一分页结构 */
export interface PageResult<T> {
  /** 当前页数据 */
  records: T[];
  /** 总记录数 */
  total: number;
  /** 当前页码 */
  pageNum: number;
  /** 每页条数 */
  pageSize: number;
}

/** 分页查询基础参数 */
export interface PageQuery {
  pageNum?: number;
  pageSize?: number;
}

/** 表格列配置(供 BaseTable 使用) */
export interface TableColumn<T = Record<string, unknown>> {
  /** 字段名 */
  prop?: string;
  /** 列标题 */
  label?: string;
  /** 列宽 */
  width?: string | number;
  /** 最小列宽 */
  minWidth?: string | number;
  /** 对齐方式 */
  align?: 'left' | 'center' | 'right';
  /** 固定列 */
  fixed?: boolean | 'left' | 'right';
  /** 是否可排序 */
  sortable?: boolean | 'custom';
  /** 内容过长时显示 tooltip */
  showOverflowTooltip?: boolean;
  /** 列类型 */
  type?: 'selection' | 'index' | 'expand';
  /** 自定义格式化 */
  formatter?: (row: T, value: unknown, index: number) => string;
  /** 自定义插槽名称(优先级高于 formatter) */
  slot?: string;
}

/** 表单字段配置(供 BaseForm / BaseSearch 使用) */
export interface FormField {
  /** 字段名 */
  prop: string;
  /** 标签 */
  label: string;
  /** 控件类型 */
  type?:
    | 'input'
    | 'textarea'
    | 'password'
    | 'number'
    | 'select'
    | 'radio'
    | 'switch'
    | 'date'
    | 'slot';
  /** 占位提示 */
  placeholder?: string;
  /** 选项(select / radio) */
  options?: Array<{ label: string; value: string | number | boolean }>;
  /** 栅格占比(24 栅格) */
  span?: number;
  /** 是否禁用 */
  disabled?: boolean;
  /** 是否可清空 */
  clearable?: boolean;
  /** 最大长度 */
  maxlength?: number;
  /** 文本域行数 */
  rows?: number;
  /** 控件宽度(查询表单常用) */
  width?: string | number;
  /** 自定义插槽名称 */
  slot?: string;
  /** 透传给控件的额外属性 */
  props?: Record<string, unknown>;
}

/* ---------------- 管理端(业务操作人员) ---------------- */

/** 管理端登录用户信息 */
export interface AdminUserInfo {
  id: number;
  username: string;
  realName: string;
  phone: string;
  email: string;
  avatar: string | null;
  roleId: number;
  roleName: string;
  roleCode: string;
  userType: 'ADMIN';
  userTypeDesc: string;
  status: number;
  remark?: string;
  createBy?: string;
  createTime?: string;
  lastLoginTime?: string;
}

/** 后台菜单(树形) */
export interface MenuItem {
  id: number;
  parentId: number;
  menuName: string;
  menuType: 'DIR' | 'MENU' | 'BUTTON';
  path: string | null;
  component: string | null;
  icon: string | null;
  perms: string | null;
  sort: number;
  visible: number;
  status: number;
  remark?: string;
  createTime?: string;
  children: MenuItem[];
}

/** 管理端角色 */
export interface RoleItem {
  id: number;
  roleCode: string;
  roleName: string;
  sort: number;
  status: number;
  remark?: string;
  userCount?: number;
  createTime?: string;
}

/** 角色详情(含已授权菜单) */
export interface RoleDetail {
  role: RoleItem;
  menuIds: number[];
}

/* ---------------- 客户端(咨询客户) ---------------- */

/** 客户端登录用户信息 */
export interface CusUserInfo {
  id: number;
  username: string | null;
  nickname: string;
  realName: string | null;
  phone: string;
  email: string | null;
  avatar: string | null;
  gender: number;
  userType: 'CUSTOMER';
  userTypeDesc: string;
  status: number;
  registerSource: string;
  createTime?: string;
  lastLoginTime?: string;
  sessionCount?: number;
}

/* ---------------- 智能客服 ---------------- */

/** 会话 */
export interface ChatSessionItem {
  id: number;
  title: string;
  lastMessage: string;
  messageCount: number;
  lastMessageTime: string;
  createTime: string;
}

/** 会话消息 */
export interface ChatMessageItem {
  id: number;
  sessionId: number;
  role: 'user' | 'assistant';
  content: string;
  createTime: string;
}

/** AI 回复结果 */
export interface ChatAnswer {
  sessionId: number;
  answer: ChatMessageItem;
  references: string[];
  category: string;
}

/** 业务分类 */
export interface CategoryItem {
  code: string;
  name: string;
  sample?: string;
}

/** 知识库条目 */
export interface KbItem {
  id: number;
  category: string;
  question: string;
  answer: string;
  keywords: string;
  hitCount: number;
  status: number;
  remark?: string;
  createBy?: string;
  createTime?: string;
  updateTime?: string;
}

/* ---------------- 日志 ---------------- */

/** 登录日志 */
export interface LoginLogItem {
  id: number;
  userId: number | null;
  username: string;
  loginType: 'ADMIN' | 'CUSTOMER';
  ip: string;
  browser: string;
  os: string;
  status: number;
  msg: string;
  loginTime: string;
}

/** 操作日志 */
export interface OperLogItem {
  id: number;
  module: string;
  operation: string;
  method: string;
  requestUri: string;
  requestMethod: string;
  params: string;
  ip: string;
  browser: string;
  os: string;
  operName: string;
  status: number;
  errorMsg: string;
  costTime: number;
  operTime: string;
}

/** 文件上传结果 */
export interface UploadResult {
  id: number;
  url: string;
  name: string;
  size: number;
}

/** 后台首页统计 */
export interface DashboardStatistics {
  sessionCount: number;
  questionCount: number;
  todaySession: number;
  kbCount: number;
  adminCount: number;
  customerCount: number;
  enabledCustomerCount: number;
  todayCustomerCount: number;
}

/* ---------------- 保单 / 理赔 ---------------- */

/** 保单 */
export interface PolicyItem {
  id: number;
  /** 保单号 */
  policyNo: string;
  /** 所属客户ID */
  cusUserId: number;
  /** 产品名称 */
  productName: string;
  /** 产品代码 */
  productCode?: string;
  /** 险种:CRITICAL/MEDICAL/ACCIDENT/LIFE/ANNUITY/OTHER */
  category: string;
  /** 投保人 */
  holderName?: string;
  /** 被保人 */
  insuredName?: string;
  /** 被保人证件号(脱敏) */
  insuredIdCard?: string;
  /** 年缴保费 */
  premium: number;
  /** 保额 */
  coverageAmount: number;
  /** 缴费年限,null 表示趸交 */
  payYears?: number | null;
  /** 生效日期 */
  effectiveDate: string;
  /** 到期日期 */
  expireDate: string;
  /** 下次缴费日期 */
  nextPayDate?: string | null;
  /** 状态:1-有效 2-中止 3-失效 4-已退保 5-满期终止 */
  status: number;
  /** 客户姓名 */
  customerName?: string;
  /** 客户手机号 */
  customerPhone?: string;
  /** 理赔次数 */
  claimCount?: number;
  createTime?: string;
  remark?: string;
}

/** 理赔案件 */
export interface ClaimItem {
  id: number;
  /** 理赔案号 */
  claimNo: string;
  cusUserId: number;
  /** 关联保单ID */
  policyId: number;
  /** 关联保单号 */
  policyNo: string;
  /** 理赔类型:MEDICAL/CRITICAL/ACCIDENT/DEATH */
  claimType: string;
  /** 出险日期 */
  accidentDate?: string;
  /** 报案日期 */
  reportDate: string;
  /** 申请金额 */
  claimAmount: number;
  /** 核定赔付金额 */
  approvedAmount?: number | null;
  /** 状态:1-已报案 2-资料审核 3-调查中 4-审核通过 5-已赔付 6-拒赔 7-已撤销 */
  status: number;
  /** 当前环节 */
  currentNode?: string;
  /** 当前处理人 */
  handler?: string;
  /** 预计赔付日 */
  expectedPayDate?: string | null;
  /** 结案日期 */
  closeDate?: string | null;
  /** 拒赔原因 */
  rejectReason?: string;
  /** 客户姓名 */
  customerName?: string;
  /** 客户手机号 */
  customerPhone?: string;
  /** 产品名称 */
  productName?: string;
  createTime?: string;
}

/** 理赔进度节点 */
export interface ClaimProgressItem {
  id: number;
  claimId: number;
  /** 环节名称 */
  node: string;
  /** 环节说明 */
  nodeDesc?: string;
  /** 处理人 */
  operatorName?: string;
  /** 发生时间 */
  progressTime: string;
  /** 是否已完成:1-已完成 0-进行中 */
  finished: number;
}

/** 理赔详情 */
export interface ClaimDetail {
  claim: ClaimItem;
  policy: PolicyItem | null;
  progress: ClaimProgressItem[];
}

/* ---------------- 大模型配置 ---------------- */

/** 大模型配置 */
export interface AiModelItem {
  id: number;
  /** 配置名称 */
  configName: string;
  /** 提供方:DASHSCOPE / OPENAI_COMPATIBLE */
  provider: string;
  /** 接口地址 */
  baseUrl?: string;
  /** API Key 掩码(仅展示,明文永不返回) */
  apiKeyMask?: string;
  /** 模型名称 */
  model: string;
  /** 采样温度 */
  temperature: number;
  /** 最大 token 数 */
  maxTokens: number;
  /** 读超时(毫秒) */
  timeoutMs: number;
  /** 是否默认:1-是 0-否 */
  isDefault: number;
  /** 状态:1-启用 0-停用 */
  status: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/** 模型连通性测试结果 */
export interface AiModelTestResult {
  success: boolean;
  /** 耗时(毫秒) */
  costMs: number;
  /** 模型回复内容 */
  reply: string;
}

