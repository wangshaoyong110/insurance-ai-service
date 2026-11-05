package com.insure.ai.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 客户端真实 IP 解析
 * <p>
 * <b>为什么不直接读 X-Forwarded-For</b>:该请求头由客户端任意伪造,
 * 若限流 key 直接取它的值,攻击者每次换一个 XFF 就能绕过 IP 频控。
 * 因此这里只在「TCP 对端确实是我们自己配置的可信代理」时才采信代理头:
 * </p>
 * <ol>
 *     <li>{@code remoteAddr} 不在 {@code trustedProxies} 中 → 直接返回 remoteAddr,XFF 一律忽略;</li>
 *     <li>在可信代理列表中 → 从右往左遍历 XFF,跳过可信代理,取第一个不可信地址作为真实客户端;</li>
 *     <li>XFF 不可用再退回 {@code X-Real-IP},仍不可用则返回 remoteAddr。</li>
 * </ol>
 * <p>
 * 默认 {@code trustedProxies} 为空,即「不信任任何代理」的安全默认值。
 * 启用前必须确认 Nginx / 网关会<b>覆盖</b>而非追加客户端传入的 XFF
 * (Nginx 用 {@code proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;} 时是追加,
 * 本工具的从右往左取值可以正确跳过伪造段)。
 * </p>
 *
 * @author shaoyong.wang
 */
public final class ClientIpUtils {

    /** 未知来源的统一标记,避免 null 拼进 Redis key */
    public static final String UNKNOWN = "unknown";

    /** 单个 IP 字符串长度上限,防止超长伪造头撑大 Redis key */
    private static final int MAX_IP_LENGTH = 64;

    private ClientIpUtils() {
    }

    /**
     * 解析客户端 IP
     *
     * @param request         当前请求
     * @param trustedProxies  可信代理 IP 列表(通常配置为网关 / Nginx 的内网地址)
     * @return 客户端 IP,永远非 null
     */
    public static String resolve(HttpServletRequest request, Collection<String> trustedProxies) {
        if (request == null) {
            return UNKNOWN;
        }
        String remote = normalize(request.getRemoteAddr());
        Set<String> trusted = normalizeSet(trustedProxies);
        // 对端不是可信代理:代理头完全不可信,直接丢弃
        if (!trusted.contains(remote)) {
            return remote.isEmpty() ? UNKNOWN : remote;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String[] hops = forwarded.split(",");
            // 从右往左:最右侧才是离我们最近的可信代理追加的地址
            for (int i = hops.length - 1; i >= 0; i--) {
                String candidate = normalize(hops[i]);
                if (!candidate.isEmpty() && !trusted.contains(candidate)) {
                    return candidate;
                }
            }
        }
        String realIp = normalize(request.getHeader("X-Real-IP"));
        if (!realIp.isEmpty() && !trusted.contains(realIp)) {
            return realIp;
        }
        return remote.isEmpty() ? UNKNOWN : remote;
    }

    /**
     * 规范化:去空白 / 去端口 / IPv6 本机等价形式统一 / 超长截断丢弃
     */
    private static String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String ip = raw.trim();
        if (ip.length() > MAX_IP_LENGTH) {
            return "";
        }
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        if (ip.startsWith("::ffff:")) {
            ip = ip.substring("::ffff:".length());
        }
        // XFF 偶见 "1.2.3.4:5678";仅当只含一个冒号时才视为端口并剥离,避免误伤 IPv6
        int firstColon = ip.indexOf(':');
        if (firstColon > 0 && firstColon == ip.lastIndexOf(':')) {
            ip = ip.substring(0, firstColon);
        }
        // IPv6 大小写统一,避免同一地址生成两个限流 key
        if (ip.indexOf(':') >= 0) {
            ip = ip.toLowerCase(Locale.ROOT);
        }
        return ip;
    }

    private static Set<String> normalizeSet(Collection<String> proxies) {
        if (proxies == null || proxies.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String proxy : proxies) {
            String ip = normalize(proxy);
            if (!ip.isEmpty()) {
                result.add(ip);
            }
        }
        return result;
    }
}
