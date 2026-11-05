package com.insure.ai.aspect;

import com.insure.ai.common.BusinessException;
import com.insure.ai.common.Constants;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.annotation.RateLimit;
import com.insure.ai.common.util.ClientIpUtils;
import com.insure.ai.common.util.SecurityUtils;
import com.insure.ai.config.SecurityProperties;
import com.insure.ai.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 限流切面
 * <p>
 * 设计取舍：
 * <ul>
 *     <li><b>降级放行</b>：Redis 不可用时只记录告警、不拦截业务。限流是保护措施，不应成为新的故障点。</li>
 *     <li><b>先 INCR 再判 TTL</b>：计数为 1 时才设置过期时间，避免每次请求都刷新 TTL 导致窗口被无限延长。</li>
 *     <li><b>主体优先取登录态</b>：按 userId 而非 IP，避免同一出口 NAT 后的多个用户互相影响。</li>
 * </ul>
 * </p>
 *
 * @author insurance-ai
 */
@Slf4j
@Aspect
@Component
@Order(1)
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate stringRedisTemplate;
    private final SecurityProperties securityProperties;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint point, RateLimit rateLimit) throws Throwable {
        String key = buildKey(point, rateLimit);
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, Duration.ofSeconds(rateLimit.seconds()));
            }
            if (count != null && count > rateLimit.limit()) {
                long remain = remainingSeconds(key, rateLimit.seconds());
                String message = StringUtils.hasText(rateLimit.message())
                        ? rateLimit.message()
                        : "操作过于频繁，请 " + remain + " 秒后再试";
                log.warn("[限流] 命中限制 key={} count={} limit={}/{}s", key, count, rateLimit.limit(), rateLimit.seconds());
                throw new BusinessException(ResultCode.TOO_MANY_REQUESTS.getCode(), message);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // Redis 异常不阻断业务，仅告警
            log.warn("[限流] Redis 不可用,本次放行: {}", e.getMessage());
        }
        return point.proceed();
    }

    /**
     * insure:rate:{业务key}:{登录用户ID 或 IP}
     */
    private String buildKey(ProceedingJoinPoint point, RateLimit rateLimit) {
        String business = StringUtils.hasText(rateLimit.key()) ? rateLimit.key() : point.getSignature().getName();
        return Constants.REDIS_RATE_LIMIT + business + ":" + principal();
    }

    private String principal() {
        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
        if (loginUser != null && loginUser.getId() != null) {
            return "u" + loginUser.getId();
        }
        return "ip" + clientIp();
    }

    /**
     * 取客户端 IP
     * <p>不直接读 X-Forwarded-For:该头可被伪造,曾导致免认证接口的 IP 限流形同虚设。
     * 统一走 {@link ClientIpUtils},只有可信代理才解析代理头。</p>
     */
    private String clientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return ClientIpUtils.UNKNOWN;
        }
        HttpServletRequest request = attributes.getRequest();
        return ClientIpUtils.resolve(request, securityProperties.getTrustedProxies());
    }

    /**
     * 剩余等待秒数，读不到时按整个窗口提示
     */
    private long remainingSeconds(String key, int windowSeconds) {
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl == null || ttl <= 0 ? windowSeconds : ttl;
    }
}
