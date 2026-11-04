package com.insure.ai.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * IP 与客户端信息解析工具
 *
 * @author insurance-ai
 */
public class WebUtils {

    /** 未知 */
    public static final String UNKNOWN = "unknown";
    /** 本机 */
    public static final String LOCALHOST_V4 = "0:0:0:0:0:0:0:1";

    private static final String[] IP_HEADERS = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_CLIENT_IP"
    };

    private WebUtils() {
    }

    /**
     * 获取客户端真实 IP(兼容 Nginx 反向代理)
     */
    public static String getIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (StringUtils.hasText(ip) && !UNKNOWN.equalsIgnoreCase(ip)) {
                // X-Forwarded-For 可能是逗号分隔的链路,取第一个
                int index = ip.indexOf(',');
                String result = index > 0 ? ip.substring(0, index) : ip;
                return normalize(result.trim());
            }
        }
        return normalize(request.getRemoteAddr());
    }

    private static String normalize(String ip) {
        if (!StringUtils.hasText(ip)) {
            return UNKNOWN;
        }
        return LOCALHOST_V4.equals(ip) ? "127.0.0.1" : ip;
    }

    /**
     * 解析浏览器类型
     */
    public static String getBrowser(HttpServletRequest request) {
        String ua = request == null ? null : request.getHeader("User-Agent");
        if (!StringUtils.hasText(ua)) {
            return UNKNOWN;
        }
        if (ua.contains("Edg/")) {
            return "Edge";
        }
        if (ua.contains("MicroMessenger")) {
            return "微信内置浏览器";
        }
        if (ua.contains("Chrome/")) {
            return "Chrome";
        }
        if (ua.contains("Firefox/")) {
            return "Firefox";
        }
        if (ua.contains("Safari/")) {
            return "Safari";
        }
        if (ua.contains("MSIE") || ua.contains("Trident")) {
            return "IE";
        }
        return "其他";
    }

    /**
     * 解析操作系统
     */
    public static String getOs(HttpServletRequest request) {
        String ua = request == null ? null : request.getHeader("User-Agent");
        if (!StringUtils.hasText(ua)) {
            return UNKNOWN;
        }
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("Mac OS X")) {
            return "macOS";
        }
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("iPhone") || ua.contains("iPad")) {
            return "iOS";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        return "其他";
    }
}
