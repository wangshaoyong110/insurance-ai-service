import { upload } from '@/utils/request';
import type { UploadResult } from '@/types';

/**
 * 文件上传接口
 */

/** 上传文件 */
export function uploadFileApi(file: File, bizType = 'common'): Promise<UploadResult> {
  return upload<UploadResult>('/file/upload', file, { bizType }, { loading: true });
}
