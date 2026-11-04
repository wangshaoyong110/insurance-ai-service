/**
 * 通用工具方法
 */

/**
 * 格式化时间
 * @param value 时间字符串 / 时间戳 / Date
 * @param withTime 是否包含时分秒
 */
export function formatDate(value?: string | number | Date, withTime = true): string {
  if (!value) {
    return '-';
  }
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  const pad = (num: number): string => String(num).padStart(2, '0');
  const day = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  if (!withTime) {
    return day;
  }
  return `${day} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

/**
 * 手机号脱敏:138****8000
 */
export function maskPhone(phone?: string): string {
  if (!phone || phone.length < 7) {
    return phone ?? '-';
  }
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`;
}

/**
 * 相对时间:刚刚 / 5分钟前 / 昨天 / 具体日期
 */
export function fromNow(value?: string): string {
  if (!value) {
    return '';
  }
  const target = new Date(value.replace(/-/g, '/')).getTime();
  if (Number.isNaN(target)) {
    return value;
  }
  const diff = Date.now() - target;
  const minute = 60 * 1000;
  const hour = 60 * minute;
  const day = 24 * hour;
  if (diff < minute) {
    return '刚刚';
  }
  if (diff < hour) {
    return `${Math.floor(diff / minute)} 分钟前`;
  }
  if (diff < day) {
    return `${Math.floor(diff / hour)} 小时前`;
  }
  if (diff < 2 * day) {
    return '昨天';
  }
  return formatDate(value, false);
}

/**
 * 复制文本到剪贴板
 */
export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

/**
 * 防抖
 */
export function debounce<T extends (...args: never[]) => void>(fn: T, delay = 300): T {
  let timer: ReturnType<typeof setTimeout> | null = null;
  return ((...args: Parameters<T>): void => {
    if (timer) {
      clearTimeout(timer);
    }
    timer = setTimeout(() => fn(...args), delay);
  }) as T;
}
