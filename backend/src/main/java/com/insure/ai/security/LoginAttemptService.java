package com.insure.ai.security;

import com.insure.ai.common.BusinessException;
import com.insure.ai.common.Constants;
import com.insure.ai.common.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录失败次数限制
 * <p>基于 Redis 计数,管理端与客户端使用不同 key 前缀,互不影响</p>
 *
 * @author insurance-ai
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    /** 登录失败次数上限 */
    private static final int MAX_LOGIN_FAIL = 5;
    /** 登录失败计数有效期(分钟) */
    private static final int LOGIN_FAIL_EXPIRE_MINUTES = 10;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 校验是否已超过失败次数上限
     *
     * @param scope   端标识(admin / customer)
     * @param account 登录账号
     */
    public void checkLimit(String scope, String account) {
        String value = stringRedisTemplate.opsForValue().get(buildKey(scope, account));
        if (value != null && Integer.parseInt(value) >= MAX_LOGIN_FAIL) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS.getCode(),
                    "密码错误次数过多,请 " + LOGIN_FAIL_EXPIRE_MINUTES + " 分钟后再试");
        }
    }

    /**
     * 累加失败次数(首次计数时设置过期时间)
     *
     * @param scope   端标识
     * @param account 登录账号
     */
    public void increase(String scope, String account) {
        String key = buildKey(scope, account);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofMinutes(LOGIN_FAIL_EXPIRE_MINUTES));
        }
    }

    /**
     * 登录成功后清空失败计数
     *
     * @param scope   端标识
     * @param account 登录账号
     */
    public void clear(String scope, String account) {
        stringRedisTemplate.delete(buildKey(scope, account));
    }

    private String buildKey(String scope, String account) {
        return Constants.REDIS_LOGIN_FAIL + scope + ":" + account;
    }
}
