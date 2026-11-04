import { createHttp } from './request';
import { getCusToken, removeCusToken } from './auth';

/**
 * 客户端(手机端 H5)http 实例
 * <p>与管理端实例相互独立:token 分开存储,登录失效跳转手机端登录页</p>
 */
export const cusHttp = createHttp({
  tokenGetter: getCusToken,
  onUnauthorized: (message: string) => {
    removeCusToken();
    ElMessage.error(message || '登录状态已失效,请重新登录');
    const { pathname, hash } = window.location;
    // 手机端使用 hash 路由,已在登录页时不再跳转,避免循环
    if (pathname.includes('mobile') && hash.startsWith('#/login')) {
      return;
    }
    window.location.href = `/mobile.html#/login?redirect=${encodeURIComponent(hash || '#/chat')}`;
  },
});

/** 客户端 GET */
export const cusGet = cusHttp.get;
/** 客户端 POST */
export const cusPost = cusHttp.post;
/** 客户端 PUT */
export const cusPut = cusHttp.put;
/** 客户端 DELETE */
export const cusDel = cusHttp.del;
/** 客户端上传 */
export const cusUpload = cusHttp.upload;
