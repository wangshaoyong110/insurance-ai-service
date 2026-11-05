# 接口清单

- 基础路径：`http://localhost:8080/api`
- 认证方式：请求头 `Authorization: Bearer {token}`（登录、注册接口除外）
- 请求/响应均为 `application/json;charset=UTF-8`
- 链路追踪：响应头返回 `X-Trace-Id`，请求携带同名头可复用该值，便于按 traceId 检索日志
- 在线文档：`http://localhost:8080/api/swagger-ui.html`（Swagger UI，可直接调试；按「管理端 / 客户端」两个分组切换）

## 在线文档（Swagger）

| 分组 | JSON 地址 | 覆盖范围 |
| --- | --- | --- |
| 管理端接口 | `/api/v3/api-docs/admin-api` | `/admin/**`（47 个接口 / 10 个分组） |
| 客户端(手机 H5)接口 | `/api/v3/api-docs/client-api` | `/auth/**`、`/chat/**`、`/file/**`（16 个接口 / 3 个分组） |

调试步骤：先调 `/admin/auth/login` 或 `/auth/login` 取 `data.token` → 点 UI 右上角 **Authorize** 填入 token（无需手写 `Bearer ` 前缀）→ 再调其他接口。

登录/注册接口在文档中已标记为无锁（`@SecurityRequirements`），无需授权即可执行。关闭文档：`springdoc.api-docs.enabled=false`。

## 两套用户体系

| | 客户端（客户） | 管理端（运维人员） |
| --- | --- | --- |
| 登录接口 | `/auth/login` | `/admin/auth/login` |
| 用户表 | `cus_user` | `sys_user` |
| JWT 载荷 `ut` | `CUSTOMER` | `ADMIN` |
| 权限 | 仅本人会话 | 角色 → 菜单授权 |
| 可访问前缀 | `/auth/**`、`/chat/**` | `/admin/**`（`@PreAuthorize("hasRole('ADMIN')")`） |

两套 token 互不通用：用客户端 token 访问 `/admin/**` 返回 `403 没有操作权限`。

## 统一响应结构

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1793761231573,
  "success": true
}
```

`code` 约定：`200` 成功；`4xx/5xx` 为标准 HTTP 语义；`1xxx` 用户相关；`2xxx` 文件相关；`3xxx` AI 会话相关；`4xxx` 安全相关；`5xxx` 系统管理（菜单 / 角色 / 客户）。完整枚举见 `common/ResultCode.java`。

> 未登录或 token 失效时 HTTP 状态码仍为 200，响应体 `code = 401`，前端拦截器据此清理登录态并跳转对应登录页。

## 分页响应结构

```json
{
  "records": [],
  "total": 100,
  "pageNum": 1,
  "pageSize": 10,
  "pages": 10
}
```

分页请求统一参数：`pageNum`（默认 1）、`pageSize`（默认 10）。

---

## 一、客户端认证 `/auth`

| 方法 | 路径 | 说明 | 参数 |
| --- | --- | --- | --- |
| POST | `/auth/login` | 客户登录 | body: `{ account, password }`，`account` 为手机号或账号 |
| POST | `/auth/sms-code` | 获取短信验证码 | body: `{ phone, scene }`，`scene` 取 `register`/`login`/`reset`；60 秒内重发返回 `4003` |
| POST | `/auth/register` | 客户注册（手机号唯一） | body: `{ phone, password, confirmPassword, smsCode, nickname?, realName?, email? }` |
| POST | `/auth/logout` | 退出登录 | header: token |
| GET | `/auth/me` | 当前客户信息 | - |
| PUT | `/auth/password` | 修改密码 | body: `{ oldPassword, newPassword, confirmPassword }` |
| PUT | `/auth/profile` | 修改资料 | query: `nickname?`, `email?` |

登录响应 `data`：

```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9...",
  "tokenPrefix": "Bearer",
  "expiresIn": 604800,
  "userInfo": {
    "id": 1, "username": "customer01", "nickname": "小张", "realName": "张三",
    "phone": "13900000001", "userType": "CUSTOMER", "registerSource": "H5",
    "createTime": "2026-11-04 11:20:28", "lastLoginTime": "2026-11-04 14:40:18"
  }
}
```

## 二、管理端认证 `/admin/auth`

| 方法 | 路径 | 说明 | 参数 |
| --- | --- | --- | --- |
| POST | `/admin/auth/login` | 管理员登录（校验角色启用状态） | body: `{ account, password }` |
| POST | `/admin/auth/logout` | 退出登录 | header: token |
| GET | `/admin/auth/me` | 当前管理员信息（含 `roleName`、`roleCode`） | - |
| GET | `/admin/auth/menus` | 当前角色可见的菜单树 | - |
| PUT | `/admin/auth/password` | 修改密码 | body: `{ oldPassword, newPassword, confirmPassword }` |

`/admin/auth/menus` 返回示例（已按角色过滤，`SUPER_ADMIN` 返回全部启用菜单）：

```json
[
  {
    "id": 1, "parentId": 0, "menuName": "数据概览", "menuType": "MENU",
    "path": "/dashboard", "component": "admin/dashboard/index", "icon": "DataLine",
    "perms": "dashboard:view", "sort": 1, "visible": 1, "status": 1,
    "children": []
  },
  {
    "id": 30, "menuName": "系统管理", "menuType": "DIR", "path": "/system",
    "children": [
      { "id": 33, "menuName": "菜单管理", "menuType": "MENU", "path": "/system/menu",
        "component": "admin/menu/index", "perms": "sys:menu:list" }
    ]
  }
]
```

> 前端把 `component`（如 `admin/menu/index`）映射为 `src/views/admin/menu/index.vue` 并动态注册路由，因此 **新增菜单后重新登录即可访问新页面**，无需改动前端代码。

## 三、智能客服 `/chat`（客户端）

| 方法 | 路径 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/chat/categories` | 业务分类与示例问题 | - |
| GET | `/chat/sessions` | 当前客户历史会话列表 | - |
| POST | `/chat/sessions` | 新建空会话 | query: `title?` |
| PUT | `/chat/sessions/{id}` | 重命名会话 | query: `title` |
| DELETE | `/chat/sessions/{id}` | 删除会话（逻辑删除，级联消息） | - |
| GET | `/chat/sessions/{id}/messages` | 会话消息分页（从最新往回翻） | query: `pageNum=1`, `pageSize=50`（上限 200） |
| POST | `/chat/send` | 发送消息并获取 AI 回复（同步） | body: `{ sessionId?, content }` |
| POST | `/chat/send/stream` | 发送消息并流式返回（SSE） | body: `{ sessionId?, content }` |

`/chat/send` 响应 `data`：

```json
{
  "sessionId": 2,
  "answer": { "id": 4, "role": "assistant", "content": "...", "model": "deepseek-chat", "tokens": 500 },
  "references": ["如何变更受益人?", "什么是保全?可以变更哪些信息?"],
  "category": "POLICY"
}
```

- `sessionId` 为空时自动创建会话，标题取首条提问前 30 字。
- `content` 上限 1000 字，超过返回 `3004`。
- 命中知识库时 `references` 返回被引用的标准问题，分类取首条命中条目。
- 同一会话携带最近 10 条历史消息作为上下文（`insure.deepseek.history-size`）；超出窗口的更早提问会被压成一句「更早的沟通要点」一起带上，避免长会话失忆。
- 知识库未命中或只有低分命中时，会往 `chat_unknown` 写一条记录，供后台「知识缺口」聚合；本轮调用过工具的提问不记（那类问题本就该查业务库，不需要补语料）。
- 接口限流 **10 次/分钟**（同步与流式共用计数），超限返回 `429`。

`/chat/sessions/{id}/messages` 响应 `data` 为分页结构（`records` 内部已按时间正序，可直接渲染）：

```json
{
  "records": [
    { "id": 1, "sessionId": 2, "role": "user", "content": "退保能退多少钱?", "createTime": "2026-11-04 10:00:00" },
    { "id": 2, "sessionId": 2, "role": "assistant", "content": "...", "model": "deepseek-chat", "tokens": 320 }
  ],
  "total": 12,
  "pageNum": 1,
  "pageSize": 50
}
```

### 流式接口 `POST /chat/send/stream`

响应 `Content-Type: text/event-stream`，按 SSE 规范分帧，事件顺序为 `meta` → `delta`（多次）→ `done` 或 `error`：

```
event:meta
data:{"sessionId":10,"references":[],"category":"OTHER"}

event:delta
data:{"t":"您好"}

event:delta
data:{"t":"！"}

event:done
data:{"sessionId":10,"messageId":21,"model":"deepseek-chat","createTime":"2026-11-04T18:12:33"}
```

| 事件 | 载荷 | 说明 |
| --- | --- | --- |
| `meta` | `{sessionId, references, category}` | 会话已创建（新会话时用它接管 ID） |
| `delta` | `{t}` | 增量文本，正文用 JSON 包装以规避换行与分帧冲突 |
| `done` | `{sessionId, messageId, model, createTime}` | 生成结束且已落库 |
| `error` | `{message}` | 失败原因，前端提示后可重试 |

调用前需已完成参数校验，因此校验类错误仍返回普通 JSON（如 `3004`、`429`），不会先建立事件流。

> 鉴权沿用 `Authorization: Bearer <token>`。浏览器侧不要用 `EventSource`（无法自定义请求头），
> 用 `fetch` + `ReadableStream`，参考 `frontend/src/utils/sse.ts`。
> 反向代理必须对该响应关闭缓冲（`proxy_buffering off`），否则会退化为一次性返回。

## 四、服务闭环 `/service`（客户端）

AI 答得好不好、答不了怎么办，这两件事都要有出口。

| 方法 | 路径 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/service/feedback-reasons` | 差评原因字典 | - |
| POST | `/service/feedback` | 对某条 AI 回复评价 | body: `{ messageId, rating, reason?, content? }` |
| GET | `/service/feedback/{messageId}` | 查询本人对某条回复的评价（刷新回显） | - |
| GET | `/service/ticket-reasons` | 转人工原因字典 | - |
| POST | `/service/tickets` | 转人工：创建工单 | body: `{ sessionId?, question, category?, reason?, priority? }` |
| GET | `/service/tickets` | 我的工单列表 | - |
| PUT | `/service/tickets/{id}/cancel` | 取消本人待处理工单 | - |

- `rating`：`1` 有用、`0` 没用；`0` 时建议带 `reason`：`WRONG` 信息有误 / `IRRELEVANT` 答非所问 / `INCOMPLETE` 不够完整 / `SLOW` 响应太慢 / `OTHER` 其他。
- 同一客户对同一条消息重复评价做 **覆盖更新**（唯一键 `message_id + cus_user_id`），不会刷出多条。
- 评价限流 **20 次/分钟**；转人工限流 **5 次/10 分钟**，避免单账号刷空单占用坐席。
- 工单号格式 `TK` + `yyyyMMdd` + 4 位序号；`priority` `1` 普通、`2` 紧急。

## 五、后台-服务工单 `/admin/ticket`

| 方法 | 路径 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/admin/ticket/page` | 工单分页（回填客户姓名与脱敏手机号） | query: `ticketNo?, status?, reason?, customer?, startDate?, endDate?, pageNum, pageSize` |
| GET | `/admin/ticket/{id}` | 工单详情 | - |
| PUT | `/admin/ticket/{id}/handle` | 接单 / 回复结单 / 取消 | body: `{ status, reply?, priority? }` |
| GET | `/admin/ticket/statistics` | 待处理 / 处理中 / 已完成 / 今日新增 / 人工接手率 | - |

`handle` 的状态流转：`1` 处理中（记录当前登录坐席与接单时间）→ `2` 已完成（写入回复与结单时间）→ `3` 已取消。客户在手机端「我的工单」可见坐席回复。

## 六、后台-回答评价 `/admin/feedback`

| 方法 | 路径 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/admin/feedback/page` | 评价分页（回填客户、AI 回复片段） | query: `rating?, reason?, customer?, startDate?, endDate?, pageNum, pageSize` |
| GET | `/admin/feedback/statistics` | 好评率、好评/差评数、差评原因分布 | - |

## 七、后台-知识缺口 `/admin/kb/gap`

| 方法 | 路径 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/admin/kb/gap/page` | 按问题聚合的缺口分页（次数降序，带最高召回得分） | query: `keyword?, status?, pageNum, pageSize` |
| GET | `/admin/kb/gap/statistics` | 待处理问题数 / 今日新增记录 / 累计记录 / 高频问题 | - |
| POST | `/admin/kb/gap/{id}/to-kb` | 一键补知识：生成知识库条目并标记已转 | body: `{ category, answer, keywords? }` |
| PUT | `/admin/kb/gap/{id}/ignore` | 忽略（闲聊类不需要覆盖的问题） | - |

## 八、后台-客户 `/admin/customer`


| 方法 | 路径 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/admin/customer/page` | 客户分页（回填 `sessionCount`） | query: `keyword?, status?, pageNum, pageSize` |
| GET | `/admin/customer/{id}` | 客户详情 | - |
| GET | `/admin/customer/{id}/profile` | **客户 360**：基本信息 + 概览指标 + 保单 + 理赔 + 最近会话 | - |
| PUT | `/admin/customer/{id}/status` | 启用/停用 | query: `status`（1/0） |
| PUT | `/admin/customer/{id}/reset-password` | 重置密码为 `123456` | - |
| DELETE | `/admin/customer/{id}` | 逻辑删除 | - |

## 九、后台-管理员账号 `/admin/user`

| 方法 | 路径 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/admin/user/page` | 分页查询 | query: `keyword?, roleId?, status?, pageNum, pageSize` |
| GET | `/admin/user/roles` | 角色下拉选项 | - |
| GET | `/admin/user/{id}` | 详情 | - |
| POST | `/admin/user` | 新增 | body: `{ username, password, realName, phone, email, roleId, status }` |
| PUT | `/admin/user/{id}` | 编辑 | body 同上（`password` 可空） |
| PUT | `/admin/user/{id}/status` | 启用/停用 | query: `status` |
| PUT | `/admin/user/{id}/reset-password` | 重置密码 | - |
| DELETE | `/admin/user/{id}` | 逻辑删除 | - |

> 内置 `admin`（`SUPER_ADMIN`）不可降级、禁用或删除。

## 十、后台-角色 `/admin/role`

| 方法 | 路径 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/admin/role/list` | 角色列表（含账号数） | - |
| GET | `/admin/role/options` | 下拉选项（仅启用） | - |
| GET | `/admin/role/{id}` | 详情（含已授权 `menuIds`） | - |
| POST | `/admin/role` | 新增 | body: `{ roleCode, roleName, sort, status, remark, menuIds }` |
| PUT | `/admin/role/{id}` | 编辑（`menuIds` 覆盖式保存） | body 同上 |
| DELETE | `/admin/role/{id}` | 删除（内置角色/有账号时禁止） | - |

## 十一、后台-菜单 `/admin/menu`

| 方法 | 路径 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/admin/menu/tree` | 菜单树 | query: `onlyEnabled`（默认 false） |
| POST | `/admin/menu` | 新增 | body: `{ parentId, menuName, menuType, path, component, icon, perms, sort, visible, status, remark }` |
| PUT | `/admin/menu/{id}` | 编辑 | body 同上 |
| DELETE | `/admin/menu/{id}` | 删除（存在子节点返回 `5002`） | - |

`menuType` 取值：`DIR` 目录、`MENU` 菜单、`BUTTON` 按钮。只有 `MENU` 且配置了 `component` 的节点会注册为前端路由。

## 十二、后台-知识库 `/admin/kb`

| 方法 | 路径 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/admin/kb/page` | 分页查询 | query: `category?, keyword?, status?, pageNum, pageSize` |
| POST | `/admin/kb` | 新增条目 | body: `{ category, question, answer, keywords?, status }` |
| PUT | `/admin/kb/{id}` | 编辑条目 | body 同上 |
| DELETE | `/admin/kb/{id}` | 逻辑删除 | - |
| GET | `/admin/kb/categories` | 分类枚举 | - |

`category`：`INSURE` 投保、`POLICY` 保全、`SURRENDER` 退保、`REDUCE` 减保、`CLAIM` 理赔、`OTHER` 其他。

## 十三、后台-日志 `/admin/log`

| 方法 | 路径 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/admin/log/login` | 登录日志分页 | query: `keyword?, status?, startDate?, endDate?, loginType?, pageNum, pageSize` |
| GET | `/admin/log/oper` | 操作日志分页 | query: `keyword?, status?, startDate?, endDate?, pageNum, pageSize` |

- 登录日志字段：`userId、username、loginType（ADMIN/CUSTOMER）、ip、browser、os、status、msg、loginTime`
- 操作日志字段：`module、operation、method、requestUri、requestMethod、params、ip、browser、os、operName、status、errorMsg、costTime、operTime`
- `params` 中密码等敏感字段自动脱敏为 `***`

## 十四、文件 `/file`

| 方法 | 路径 | 说明 | 参数 |
| --- | --- | --- | --- |
| POST | `/file/upload` | 上传文件 | form-data: `file`，query: `bizType?` |

允许扩展名：`jpg/jpeg/png/gif/webp/bmp/pdf/doc/docx/xls/xlsx/ppt/pptx/txt/csv`；单文件上限 20MB。
响应 `data`：`{ id, url, name, size }`，`url` 形如 `/api/upload/20261104/xxxx.png`。

## 十五、后台-概览 `/admin/dashboard`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/admin/dashboard/statistics` | 会话数、提问数、今日新增会话、知识库条数、后台员工数、注册客户数、启用客户数、今日新增客户，以及**回答评价**与**工单**两组子统计 |
响应 `data` 示例：

```json
{
  "questionCount": 40, "sessionCount": 12, "kbCount": 8, "todaySession": 3,
  "adminCount": 2, "adminType": "ADMIN",
  "customerCount": 1, "enabledCustomerCount": 1, "todayCustomerCount": 1,
  "customerType": "CUSTOMER",
  "feedback": { "total": 1, "good": 0, "bad": 1, "goodRate": 0, "reasonDist": { "INCOMPLETE": 1 } },
  "ticket": { "pending": 0, "processing": 0, "finished": 1, "todayNew": 1, "acceptRate": 100 }
}
```

> `goodRate` / `acceptRate` 在无数据时为 `null` / `0`，前端显示为「暂无评价」而不是 0%，避免误判。

---

## 十六、后台-AI 模型配置 `/admin/model`

> 需要管理端 token。API Key 明文**只进不出**：提交后由后端国密 **SM4-GCM** 加密入库（密文带 `SM4:` 前缀），查询接口只返回掩码；历史 AES 密文在启动时自动迁移为 SM4。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/admin/model/list` | 全部配置，`apiKey` 字段已脱敏为 `apiKeyMask`（如 `sk-****cdef`） |
| POST | `/admin/model` | 新增配置 |
| PUT | `/admin/model/{id}` | 编辑配置（`apiKey` 传空表示保持原 Key 不变） |
| DELETE | `/admin/model/{id}` | 删除配置 |
| PUT | `/admin/model/{id}/status?status=1` | 启用 / 停用（1 启用，0 停用） |
| PUT | `/admin/model/{id}/default` | 设为默认（同时只能有一条默认） |
| POST | `/admin/model/test` | 连通性测试，真实发一次短对话 |

新增 / 编辑 body：

```json
{
  "configName": "DeepSeek-默认",
  "provider": "OPENAI_COMPATIBLE",
  "model": "deepseek-chat",
  "baseUrl": "https://api.deepseek.com",
  "apiKey": "sk-xxxxxxxxxxxxxxxx",
  "temperature": 0.3,
  "maxTokens": 2048,
  "status": 1,
  "isDefault": 1
}
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `configName` | 是 | ≤ 64 字符 |
| `provider` | 是 | `OPENAI_COMPATIBLE`（DeepSeek / vLLM / Ollama / 内网网关）或 `DASHSCOPE`（阿里百炼） |
| `model` | 是 | 模型名称，如 `deepseek-chat`、`qwen-plus` |
| `baseUrl` | 否 | 留空用厂商默认；内网填网关地址 |
| `apiKey` | 新增必填 | 明文，最长 500；**响应中不会返回明文** |
| `temperature` | 否 | 0.0 ~ 2.0 |
| `maxTokens` | 否 | 1 ~ 100000 |

连通性测试 body 支持两种用法：

```json
{ "id": 1 }                                                   // 测已保存的配置（用库中密钥解密）
{ "provider":"OPENAI_COMPATIBLE", "model":"deepseek-chat",     // 测未保存的草稿
  "baseUrl":"https://api.deepseek.com", "apiKey":"sk-xxx" }
```

响应 `data`：`{ "success": true, "costMs": 820, "reply": "pong" }`

> 生效规则：只有 **启用 + 默认** 的那一条会用于对话；保存后下一次提问立即生效，无需重启。

## 十七、后台-保单理赔 `/admin/policy` `/admin/claim`

> 只读接口。数据由核心业务系统同步，后台与 AI 工具读同一份表，保证答复一致。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/admin/policy/page` | 保单分页 |
| GET | `/admin/policy/{id}` | 保单详情 |
| GET | `/admin/claim/page` | 理赔分页 |
| GET | `/admin/claim/{id}` | 理赔详情 + 进度时间轴 |

保单查询参数（`PolicyQuery extends PageQuery`）：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `policyNo` | string | 保单号，模糊 |
| `customer` | string | 客户姓名 / 手机号，模糊 |
| `category` | string | 险种：`CRITICAL` `MEDICAL` `ACCIDENT` `LIFE` `ANNUITY` `OTHER` |
| `status` | int | 1 有效 / 2 中止 / 3 失效 / 4 已退保 / 5 满期终止 |
| `pageNum` `pageSize` | int | 分页，默认 1 / 10 |

理赔查询参数（`ClaimQuery extends PageQuery`）：`claimNo`（模糊）、`policyNo`（模糊）、`customer`（模糊）、`claimType`、`status`（1 已报案 / 2 资料审核 / 3 调查中 / 4 审核通过 / 5 已赔付 / 6 拒赔 / 7 已撤销）。

理赔详情响应 `data`：

```json
{
  "claim": {
    "claimNo": "CLM20261025003", "policyNo": "P202501100003",
    "claimType": "ACCIDENT", "claimAmount": 3200.00, "approvedAmount": null,
    "reportDate": "2026-10-25", "status": 2, "currentNode": "资料审核",
    "handler": "李经理", "expectedPayDate": "2026-11-05"
  },
  "policy": { "policyNo": "P202501100003", "productName": "畅行无忧综合意外险" },
  "progress": [
    { "id": 1, "node": "已报案", "nodeDesc": "客户线上报案", "progressTime": "2026-10-25 09:12:00", "finished": 1, "operatorName": "系统" },
    { "id": 2, "node": "资料审核", "nodeDesc": "核验门诊病历与发票", "progressTime": "2026-10-26 10:30:00", "finished": 0, "operatorName": "李经理" }
  ]
}
```

> 越权说明：以上均为**管理端**接口，按角色/菜单控制。客户端（手机 H5）**没有**开放的保单查询接口，客户只能通过对话让 AI 调工具查询，而工具强制以登录态 `cusUserId` 过滤，无法越权查看他人数据。

---

## 十八、典型调用示例

```bash
# ---------- 管理端 ----------
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"account":"admin","password":"123456"}' | jq -r '.data.token')

# 当前管理员
curl -s http://localhost:8080/api/admin/auth/me -H "Authorization: Bearer $ADMIN_TOKEN"

# 菜单树（驱动后台导航）
curl -s http://localhost:8080/api/admin/auth/menus -H "Authorization: Bearer $ADMIN_TOKEN"

# 新增角色并授权菜单
curl -s -X POST http://localhost:8080/api/admin/role \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"roleCode":"AUDITOR","roleName":"审计员","sort":3,"status":1,"menuIds":[1,40,41]}'

# ---------- 客户端 ----------
CUS_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"account":"13900000001","password":"123456"}' | jq -r '.data.token')

# 提问
curl -s -X POST http://localhost:8080/api/chat/send \
  -H "Authorization: Bearer $CUS_TOKEN" -H "Content-Type: application/json" \
  -d '{"content":"退保能退多少钱?犹豫期是多久?"}'

# 历史会话
curl -s http://localhost:8080/api/chat/sessions -H "Authorization: Bearer $CUS_TOKEN"

# ---------- 服务闭环 ----------
# 评价 AI 回复(1 有用 / 0 没用)
curl -s -X POST http://localhost:8080/api/service/feedback \
  -H "Authorization: Bearer $CUS_TOKEN" -H "Content-Type: application/json" \
  -d '{"messageId":42,"rating":0,"reason":"INCOMPLETE","content":"希望举个例子"}'

# 转人工
curl -s -X POST http://localhost:8080/api/service/tickets \
  -H "Authorization: Bearer $CUS_TOKEN" -H "Content-Type: application/json" \
  -d '{"sessionId":20,"question":"等待期多久?","reason":"COMPLEX","priority":2}'

# 坐席接单 → 回复结单
curl -s -X PUT http://localhost:8080/api/admin/ticket/1/handle \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"status":1}'
curl -s -X PUT http://localhost:8080/api/admin/ticket/1/handle \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"status":2,"reply":"您的保单已过等待期"}'

# 好评率 / 工单待办
curl -s http://localhost:8080/api/admin/feedback/statistics -H "Authorization: Bearer $ADMIN_TOKEN"
curl -s http://localhost:8080/api/admin/ticket/statistics -H "Authorization: Bearer $ADMIN_TOKEN"

# ---------- 知识缺口 ----------
curl -s "http://localhost:8080/api/admin/kb/gap/page?pageNum=1&pageSize=10" -H "Authorization: Bearer $ADMIN_TOKEN"
# 一键补知识(会生成条目并把该问题标记为已转)
curl -s -X POST http://localhost:8080/api/admin/kb/gap/1/to-kb \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"category":"OTHER","answer":"...","keywords":"关键词1,关键词2"}'

# 客户 360
curl -s http://localhost:8080/api/admin/customer/1/profile -H "Authorization: Bearer $ADMIN_TOKEN"

# ---------- 模型配置 ----------
# 看配置列表(apiKey 只会是掩码)
curl -s http://localhost:8080/api/admin/model/list -H "Authorization: Bearer $ADMIN_TOKEN"

# 新增一条内网网关配置
curl -s -X POST http://localhost:8080/api/admin/model \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"configName":"内网-Qwen","provider":"OPENAI_COMPATIBLE","model":"qwen2.5-14b-instruct",
       "baseUrl":"http://10.0.0.20:8000/v1","apiKey":"sk-internal-xxx","temperature":0.2,"status":1,"isDefault":0}'

# 测连通性(用库中密钥,不传 apiKey)
curl -s -X POST http://localhost:8080/api/admin/model/test \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"id":2}'

# 设为默认(改完下一次提问即生效)
curl -s -X PUT http://localhost:8080/api/admin/model/2/default -H "Authorization: Bearer $ADMIN_TOKEN"

# ---------- 保单理赔 ----------
curl -s "http://localhost:8080/api/admin/policy/page?pageNum=1&pageSize=10&status=1" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

curl -s "http://localhost:8080/api/admin/claim/page?pageNum=1&pageSize=10&customer=张" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# 理赔详情含进度时间轴
curl -s http://localhost:8080/api/admin/claim/1 -H "Authorization: Bearer $ADMIN_TOKEN"

# ---------- AI 工具调用(客户端提问触发) ----------
curl -s -X POST http://localhost:8080/api/chat/send \
  -H "Authorization: Bearer $CUS_TOKEN" -H "Content-Type: application/json" \
  -d '{"content":"我名下有哪些保单?保额分别多少?"}'

# 越权验证:用他人保单号提问,应回复"未查询到"而非返回数据
curl -s -X POST http://localhost:8080/api/chat/send \
  -H "Authorization: Bearer $CUS_TOKEN" -H "Content-Type: application/json" \
  -d '{"content":"帮我查一下保单 P202403010002 的理赔进度"}'

# ---------- 权限隔离验证 ----------
# 用客户端 token 访问管理端接口,应返回 code=403
curl -s http://localhost:8080/api/admin/user/page -H "Authorization: Bearer $CUS_TOKEN"
```

访问异常排查：

```bash
# 用响应头里的 traceId 检索当天归档日志
zgrep "test-trace-001" backend/logs/*/app.*.log.gz
```
