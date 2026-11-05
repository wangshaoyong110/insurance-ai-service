package com.insure.ai.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客户端 IP 解析单测
 * <p>核心回归点:免认证接口的 IP 限流绝不能被客户端伪造的 X-Forwarded-For 绕过</p>
 *
 * @author shaoyong.wang
 */
@DisplayName("客户端 IP 解析与防伪造")
class ClientIpUtilsTest {

    private static MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    @Test
    @DisplayName("未配置可信代理时,伪造的 XFF 一律忽略,按 TCP 对端计数")
    void forgedXffShouldBeIgnoredWithoutTrustedProxy() {
        MockHttpServletRequest request = request("10.1.2.3");
        request.addHeader("X-Forwarded-For", "8.8.8.8, 6.6.6.6");
        request.addHeader("X-Real-IP", "9.9.9.9");

        assertThat(ClientIpUtils.resolve(request, List.of())).isEqualTo("10.1.2.3");
    }

    @Test
    @DisplayName("对端不是可信代理时,即使伪造了 XFF 也拿不到独立的限流 key")
    void forgedXffCannotSplitRateLimitKey() {
        MockHttpServletRequest first = request("10.1.2.3");
        first.addHeader("X-Forwarded-For", "1.1.1.1");
        MockHttpServletRequest second = request("10.1.2.3");
        second.addHeader("X-Forwarded-For", "2.2.2.2");

        assertThat(ClientIpUtils.resolve(first, List.of("127.0.0.1")))
                .isEqualTo(ClientIpUtils.resolve(second, List.of("127.0.0.1")))
                .isEqualTo("10.1.2.3");
    }

    @Test
    @DisplayName("可信代理下从右往左取值,跳过客户端伪造的前缀段")
    void trustedProxyShouldSkipForgedPrefix() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "1.1.1.1, 203.0.113.9");

        assertThat(ClientIpUtils.resolve(request, List.of("127.0.0.1"))).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("多级可信代理链应跳过所有可信跳转,取最左侧真实客户端")
    void multiHopProxyShouldSkipTrustedHops() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.8");

        assertThat(ClientIpUtils.resolve(request, List.of("127.0.0.1", "10.0.0.8")))
                .isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("XFF 缺失时回退 X-Real-IP")
    void shouldFallbackToRealIpHeader() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Real-IP", "203.0.113.77");

        assertThat(ClientIpUtils.resolve(request, List.of("127.0.0.1"))).isEqualTo("203.0.113.77");
    }

    @Test
    @DisplayName("IPv6 本机地址归一化,避免同一来源产生两个限流 key")
    void shouldNormalizeIpv6Loopback() {
        assertThat(ClientIpUtils.resolve(request("0:0:0:0:0:0:0:1"), List.of()))
                .isEqualTo("127.0.0.1");
        assertThat(ClientIpUtils.resolve(request("::ffff:192.0.2.1"), List.of()))
                .isEqualTo("192.0.2.1");
    }

    @Test
    @DisplayName("超长伪造头被丢弃,不撑大 Redis key")
    void overlongHeaderShouldBeDiscarded() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "x".repeat(200));

        assertThat(ClientIpUtils.resolve(request, List.of("127.0.0.1"))).isEqualTo("127.0.0.1");
    }
}
