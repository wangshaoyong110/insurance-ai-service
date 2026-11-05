/**
 * 上传前的大小与类型预校验
 * <p>
 * 后端才是最终防线(见 UploadProperties 与 FileServiceImpl),这里提前拦一次,
 * 避免 50MB 视频传到一半才被拒绝,白白浪费用户流量。
 * 阈值需与后端 insure.upload.biz-limits 保持一致。
 * </p>
 */

/** 全局硬上限(MB) */
export const GLOBAL_MAX_MB = 50;

/** 分业务限额(MB) */
export const UPLOAD_LIMITS: Record<string, number> = {
  avatar: 2,
  kb: 20,
  'chat-image': 10,
  'chat-video': 50,
  common: 20,
};

const IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'];
const VIDEO_EXTENSIONS = ['mp4', 'mov', 'm4v', 'webm', 'ogv'];

/** 聊天图片可选类型 */
export const CHAT_IMAGE_ACCEPT = 'image/jpeg,image/png,image/gif,image/webp,image/bmp';
/** 聊天视频可选类型 */
export const CHAT_VIDEO_ACCEPT = 'video/mp4,video/quicktime,video/webm,video/ogg';

function extensionOf(file: File): string {
  const index = file.name.lastIndexOf('.');
  return index < 0 ? '' : file.name.slice(index + 1).toLowerCase();
}

function resolveKey(file: File, bizType: string): string {
  if (bizType === 'chat') {
    const ext = extensionOf(file);
    if (VIDEO_EXTENSIONS.includes(ext)) {
      return 'chat-video';
    }
    if (IMAGE_EXTENSIONS.includes(ext)) {
      return 'chat-image';
    }
  }
  return bizType || 'common';
}

/**
 * 该文件在该业务下的上限(MB)
 */
export function uploadLimitMb(file: File, bizType = 'common'): number {
  const key = resolveKey(file, bizType);
  const limit = UPLOAD_LIMITS[key] ?? UPLOAD_LIMITS.common;
  return Math.min(limit, GLOBAL_MAX_MB);
}

/**
 * 校验文件,返回错误提示;通过返回 null
 */
export function checkUpload(file: File, bizType = 'common'): string | null {
  const ext = extensionOf(file);
  if (!ext) {
    return '无法识别文件类型,请重新选择';
  }
  const allowed = [...IMAGE_EXTENSIONS, ...VIDEO_EXTENSIONS, 'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'csv'];
  if (!allowed.includes(ext)) {
    return `不支持的文件类型: .${ext}`;
  }
  const limit = uploadLimitMb(file, bizType);
  if (file.size > limit * 1024 * 1024) {
    return `文件大小超出限制(最大 ${limit}MB)`;
  }
  if (file.size === 0) {
    return '文件内容为空,请重新选择';
  }
  return null;
}

/**
 * 字节数格式化为可读文本
 */
export function formatFileSize(bytes?: number | null): string {
  if (!bytes) {
    return '';
  }
  if (bytes >= 1024 * 1024) {
    return `${(bytes / 1024 / 1024).toFixed(1)}MB`;
  }
  return `${Math.max(1, Math.round(bytes / 1024))}KB`;
}
