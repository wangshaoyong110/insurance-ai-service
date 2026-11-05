# 反向代理公共参数（被各 location include）
# 保留真实客户端 IP，供后端做短信防刷与登录日志

proxy_http_version 1.1;
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;

proxy_connect_timeout 10s;
proxy_read_timeout 120s;
proxy_send_timeout 120s;
