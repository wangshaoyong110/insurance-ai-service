/**
 * POST + SSE 流式请求封装
 * <p>
 * 为什么不用原生 EventSource：它只支持 GET，且无法自定义请求头，
 * 而本项目的鉴权依赖 Authorization: Bearer。这里用 fetch 读取
 * ReadableStream，手动按 SSE 规范分帧解析。
 * </p>
 * <p>事件协议（与后端 ChatService.sendStream 对应）：meta → delta* → done | error</p>
 */

/** 后端事件负载 */
export interface SseMetaPayload {
  sessionId: number;
  references?: string[];
  category?: string;
  /** 未实名客户问及本人业务数据:后端返回引导话术,前端展示「去实名认证」入口 */
  needRealName?: boolean;
}

export interface SseDeltaPayload {
  /** 增量文本 */
  t: string;
}

export interface SseDonePayload {
  sessionId: number;
  messageId: number;
  model?: string;
  createTime?: string;
}

export interface SseErrorPayload {
  message: string;
}

export interface SseHandlers {
  onMeta?: (payload: SseMetaPayload) => void;
  onDelta?: (text: string) => void;
  onDone?: (payload: SseDonePayload) => void;
  onError?: (message: string) => void;
}

/** 单帧解析结果 */
interface SseFrame {
  event: string;
  data: string;
}

/**
 * 解析一个 SSE 帧（形如 "event:delta\ndata:{...}"）
 */
function parseFrame(raw: string): SseFrame {
  let event = 'message';
  const dataLines: string[] = [];
  for (const line of raw.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      // 规范规定 data: 之后的一个空格不属于数据
      dataLines.push(line.slice(5).replace(/^ /, ''));
    }
  }
  return { event, data: dataLines.join('\n') };
}

/**
 * 发起流式请求
 *
 * @param path     接口路径（相对 baseURL，例如 /chat/send/stream）
 * @param body     请求体
 * @param handlers 各事件回调
 * @param signal   用于取消（组件卸载时中断连接）
 */
export async function postSse(
  path: string,
  body: unknown,
  handlers: SseHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const baseURL = import.meta.env.VITE_API_BASE_URL ?? '/api';
  // 动态导入避免与 request.ts 形成循环依赖
  const { getCusToken } = await import('./auth');

  const response = await fetch(`${baseURL}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      Authorization: `Bearer ${getCusToken()}`,
    },
    body: JSON.stringify(body),
    signal,
  });

  if (!response.ok) {
    // 校验失败/限流等场景后端返回的是普通 JSON，而不是事件流
    let message = `请求失败(${response.status})`;
    try {
      const text = await response.text();
      const parsed = JSON.parse(text) as { message?: string };
      if (parsed?.message) {
        message = parsed.message;
      }
    } catch {
      // 保持默认提示
    }
    handlers.onError?.(message);
    return;
  }

  if (!response.body) {
    handlers.onError?.('当前浏览器不支持流式响应');
    return;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      // 兼容 CRLF：不同容器/代理可能改写行尾
      buffer = buffer.replace(/\r\n/g, '\n');

      let boundary = buffer.indexOf('\n\n');
      while (boundary >= 0) {
        const raw = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        if (raw.trim()) {
          dispatch(parseFrame(raw), handlers);
        }
        boundary = buffer.indexOf('\n\n');
      }
    }
  } finally {
    reader.releaseLock();
  }
}

/**
 * 按事件名分发
 */
function dispatch(frame: SseFrame, handlers: SseHandlers): void {
  try {
    switch (frame.event) {
      case 'meta':
        handlers.onMeta?.(JSON.parse(frame.data) as SseMetaPayload);
        break;
      case 'delta':
        handlers.onDelta?.((JSON.parse(frame.data) as SseDeltaPayload).t);
        break;
      case 'done':
        handlers.onDone?.(JSON.parse(frame.data) as SseDonePayload);
        break;
      case 'error':
        handlers.onError?.((JSON.parse(frame.data) as SseErrorPayload).message);
        break;
      default:
        break;
    }
  } catch {
    // 单帧解析失败不应中断整个流
  }
}
