package com.insure.ai.security;

import com.insure.ai.common.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Token 状态管理:退出登录后把 token 放入 Redis 黑名单,实现服务端可控失效
 *
 * @author insurance-ai
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 将 token 加入黑名单
     *
     * @param token      待失效的 token
     * @param ttlSeconds 剩余有效期(秒),小于等于 0 时不再写入
     */
    public void addBlacklist(String token, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }
        stringRedisTemplate.opsForValue()
                .set(Constants.REDIS_TOKEN_BLACKLIST + token, "1", Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 判断 token 是否已失效
     */
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(Constants.REDIS_TOKEN_BLACKLIST + token));
    }
}
