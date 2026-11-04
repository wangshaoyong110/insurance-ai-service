import type { App } from 'vue';
import { ElMessage } from 'element-plus';

/**
 * 全局异常兜底
 * <p>
 * 没有这一层时，组件里任何未捕获的异常都只会打一行控制台日志，
 * 用户看到的是白屏或半截页面，且完全不知道发生了什么。
 * </p>
 */

/** 同一错误 3 秒内只提示一次，避免组件循环报错把屏幕刷满 */
let lastMessage = '';
let lastMessageAt = 0;

function notify(message: string): void {
  const now = Date.now();
  if (message === lastMessage && now - lastMessageAt < 3000) {
    return;
  }
  lastMessage = message;
  lastMessageAt = now;
  ElMessage.error(message);
}

/**
 * 安装全局错误处理
 *
 * @param app   应用实例
 * @param scope 来源标识（admin / mobile），便于区分两个入口的日志
 */
export function installErrorHandler(app: App, scope: 'admin' | 'mobile'): void {
  app.config.errorHandler = (err, _instance, info) => {
    console.error(`[${scope}] 未捕获异常 (${info})`, err);
    notify('页面出现异常，请刷新后重试');
  };

  // Promise 异常：业务请求失败已由 axios 拦截器统一提示，这里只兜住漏网的
  window.addEventListener('unhandledrejection', (event) => {
    console.error(`[${scope}] 未处理的 Promise 异常`, event.reason);
  });

  // 资源加载失败：发版后旧的 chunk 已被替换时最常见，需要明确的提示而不是白屏
  window.addEventListener(
    'error',
    (event) => {
      const target = event.target as HTMLElement | null;
      if (!target) {
        return;
      }
      if (target.tagName === 'SCRIPT' || target.tagName === 'LINK') {
        const url = (target as HTMLScriptElement).src || (target as HTMLLinkElement).href;
        console.warn(`[${scope}] 静态资源加载失败，可能是版本已更新`, url);
        notify('页面资源已更新，请刷新后重试');
      }
    },
    true,
  );
}
