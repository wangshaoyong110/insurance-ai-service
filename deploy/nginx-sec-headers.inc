# 统一的安全响应头
# 注意：Nginx 的 add_header 只在「当前层级没有 add_header」时才继承上级，
# 所以凡是在 location 里写过 add_header 的地方，都要再 include 本文件一次。

# 禁止浏览器猜测 MIME 类型（防止上传的 html 被当成页面执行）
add_header X-Content-Type-Options "nosniff" always;
# 禁止被第三方站点用 iframe 嵌套（防点击劫持）
add_header X-Frame-Options "SAMEORIGIN" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
# 关闭不需要的浏览器能力
add_header Permissions-Policy "geolocation=(), microphone=(), camera=(), payment=(), usb=()" always;
add_header Cross-Origin-Opener-Policy "same-origin" always;
add_header X-Permitted-Cross-Domain-Policies "none" always;

# CSP：前端所有资源都同源（已确认无外部 CDN / 字体），因此可以收得很紧。
#   script-src 'self'          —— 只跑本站打包出来的 JS，杜绝外挂脚本
#   style-src  'self' unsafe-inline —— Element Plus 会注入内联样式，这里必须放行
#   img/media blob:            —— 聊天图片、视频预览走 blob URL
#   connect-src 'self'         —— 只允许同源 XHR / SSE
#   frame-ancestors 'self'     —— 与 X-Frame-Options 双重保险
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; media-src 'self' blob:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'self'" always;
