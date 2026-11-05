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
  /** 服务模式:0-AI自助 1-排队等人工 2-人工接入中 */
  serviceMode?: number;
  /** 接管坐席姓名 */
  handlerName?: string | null;
  /** 关联工单ID */
  ticketId?: number | null;
  lastMessage: string;
  messageCount: number;
  lastMessageTime: string;
  createTime: string;
}

/** 会话消息 */
export interface ChatMessageItem {
  id: number;
  sessionId: number;
  /** staff = 人工客服，system = 系统提示 */
  role: 'user' | 'assistant' | 'staff' | 'system';
  /** text / image / video / system */
  msgType?: string;
  content: string;
  /** 图片/视频地址 */
  mediaUrl?: string | null;
  mediaName?: string | null;
  mediaSize?: number | null;
  /** 发送者显示名(坐席) */
  senderName?: string | null;
  createTime: string;
  /**
   * 后端真实消息ID
   * <p>流式回复先以负数临时 id 占位，done 事件才拿到真实 id；
   * 不直接改 id 是为了避免 v-for key 变化导致节点重建、滚动跳动</p>
   */
  messageId?: number;
  /** 评价状态:1-有用 0-没用，undefined 表示未评价 */
  feedback?: number;
  /** 本地提示消息(转人工结果等)，不展示评价操作 */
  notice?: boolean;
}

/** AI 回复结果 */
export interface ChatAnswer {
  sessionId: number;
  /** 人工接入时为空,前端不应再等待 AI 回复 */
  answer?: ChatMessageItem | null;
  references: string[];
  category: string;
  /** 0-AI 2-人工接入中 */
  serviceMode?: number;
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
  /** image / video / file */
  type?: string;
  /** 该业务允许的最大值(便于提示) */
  limit?: string;
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
  /** AI 回答评价统计 */
  feedback: FeedbackStatistics;
  /** 人工工单统计 */
  ticket: TicketStatistics;
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


/* ---------------- 服务闭环：评价与工单 ---------------- */

/** 人工工单 */
export interface TicketItem {
  id: number;
  /** 工单号,如 TK202611040001 */
  ticketNo: string;
  cusUserId: number;
  sessionId?: number | null;
  /** 客户问题摘要 */
  question: string;
  /** 业务分类 */
  category: string;
  /** 转人工原因 */
  reason: string;
  /** 状态:0-待处理 1-处理中 2-已完成 3-已取消 */
  status: number;
  /** 优先级:1-普通 2-紧急 */
  priority: number;
  handlerId?: number | null;
  handlerName?: string | null;
  /** 坐席回复(客户可见) */
  reply?: string | null;
  acceptTime?: string | null;
  finishTime?: string | null;
  createTime: string;
  /** 客户展示名(管理端回填) */
  customerName?: string;
  /** 客户手机号(已脱敏) */
  customerPhone?: string;
}

/** AI 回答评价 */
export interface FeedbackItem {
  id: number;
  messageId: number;
  sessionId: number;
  cusUserId: number;
  /** 1-有用 0-没用 */
  rating: number;
  /** 差评原因 */
  reason?: string | null;
  content?: string | null;
  customerName?: string;
  customerPhone?: string;
  /** AI 回复片段 */
  answerPreview?: string;
  /** 评价时间 */
  feedbackTime?: string;
  createTime: string;
}

/** 评价统计 */
export interface FeedbackStatistics {
  total: number;
  good: number;
  bad: number;
  /** 好评率(%),无评价时为 null */
  goodRate: number | null;
  reasonDist: Record<string, number>;
}

/** 工单统计 */
export interface TicketStatistics {
  pending: number;
  processing: number;
  finished: number;
  todayNew: number;
  /** 人工接手率(%) */
  acceptRate: number;
}

/** 选项字典(原因/分类等) */
export interface OptionItem {
  code: string;
  name: string;
}

/** 客户 360 概览指标 */
export interface CustomerProfileSummary {
  policyCount: number;
  activePolicyCount: number;
  activeCoverage: number;
  claimCount: number;
  openClaimCount: number;
  sessionCount?: number;
}

/** 客户 360 视图 */
export interface CustomerProfile {
  customer: CusUserInfo;
  summary: CustomerProfileSummary;
  policies: PolicyItem[];
  claims: ClaimItem[];
  sessions: ChatSessionItem[];
}

/** 知识缺口聚合项 */
export interface GapGroupItem {
  question: string;
  /** 出现次数 */
  hitCount: number;
  /** 最高召回得分,0 表示完全没命中 */
  bestScore: number;
  /** 0-待处理 1-已转知识 2-已忽略 */
  status: number;
  /** 代表记录ID,用于转知识/忽略 */
  sampleId: number;
  lastTime: string;
}

/** 知识缺口统计 */
export interface GapStatistics {
  pendingGroups: number;
  todayRecords: number;
  totalRecords: number;
  topQuestions: string[];
}
