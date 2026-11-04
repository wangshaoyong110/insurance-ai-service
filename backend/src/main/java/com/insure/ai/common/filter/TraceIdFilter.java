package com.insure.ai.common.filter;

import com.insure.ai.common.Constants;
import com.insure.ai.common.util.WebUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪过滤器
 * <p>为每个请求生成 traceId 写入 MDC,日志格式中输出后即可通过 traceId 串联一次请求的全部日志;</p>
 * <p>同时记录访问日志(方法、路径、IP、耗时、状态码),便于问题定位</p>
 *
 * @author insurance-ai
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** traceId 长度 */
    private static final int TRACE_ID_LENGTH = 16;
    /** 静态资源与健康检查不记录访问日志 */
    private static final String[] IGNORE_URI_PREFIX = {"/upload/", "/favicon.ico", "/error"};

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String traceId = resolveTraceId(request);
        MDC.put(Constants.TRACE_ID, traceId);
        response.setHeader(Constants.TRACE_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (!isIgnored(request.getRequestURI())) {
                log.info("[访问] {} {} status={} cost={}ms ip={} traceId={}",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), cost,
                        WebUtils.getIp(request), traceId);
            }
            // MDC 必须清理,避免线程复用时 traceId 串号
            MDC.remove(Constants.TRACE_ID);
        }
    }

    /**
     * 优先复用上游传入的 traceId(便于网关/前端透传),否则本地生成
     */
    private String resolveTraceId(HttpServletRequest request) {
        String header = request.getHeader(Constants.TRACE_HEADER);
        if (StringUtils.hasText(header)) {
            return header.length() > 64 ? header.substring(0, 64) : header;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, TRACE_ID_LENGTH);
    }

    /**
     * 判断是否忽略访问日志
     */
    private boolean isIgnored(String uri) {
        if (uri == null) {
            return true;
        }
        for (String prefix : IGNORE_URI_PREFIX) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
