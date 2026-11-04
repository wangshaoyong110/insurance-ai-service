package com.insure.ai.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类:负责 token 的生成、解析与校验
 *
 * @author insurance-ai
 */
@Slf4j
@Component
public class JwtUtils {

    /** 载荷键:用户ID */
    public static final String CLAIM_USER_ID = "uid";
    /** 载荷键:用户类型 */
    public static final String CLAIM_USER_TYPE = "ut";
    /** 载荷键:真实姓名 */
    public static final String CLAIM_REAL_NAME = "rn";
    /** 载荷键:手机号 */
    public static final String CLAIM_PHONE = "ph";
    /** 载荷键:管理端角色标识 */
    public static final String CLAIM_ROLE_CODE = "rc";

    @Value("${insure.jwt.secret}")
    private String secret;

    @Value("${insure.jwt.expire}")
    private Long expire;

    @Value("${insure.jwt.prefix}")
    private String prefix;

    @Value("${insure.jwt.header}")
    private String header;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        // HS256 要求密钥长度不少于 32 字节
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 token
     */
    public String generateToken(LoginUser loginUser) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expire);
        return Jwts.builder()
                .subject(loginUser.getUsername())
                .claim(CLAIM_USER_ID, loginUser.getId())
                .claim(CLAIM_USER_TYPE, loginUser.getUserType())
                .claim(CLAIM_REAL_NAME, loginUser.getRealName())
                .claim(CLAIM_PHONE, loginUser.getPhone())
                .claim(CLAIM_ROLE_CODE, loginUser.getRoleCode())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析 token,失败返回 null
     */
    public Claims parseToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("[JWT] token 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 由 token 还原登录用户
     */
    public LoginUser toLoginUser(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        LoginUser loginUser = new LoginUser();
        loginUser.setId(claims.get(CLAIM_USER_ID, Long.class));
        loginUser.setUsername(claims.getSubject());
        loginUser.setUserType(claims.get(CLAIM_USER_TYPE, String.class));
        loginUser.setRealName(claims.get(CLAIM_REAL_NAME, String.class));
        loginUser.setPhone(claims.get(CLAIM_PHONE, String.class));
        loginUser.setRoleCode(claims.get(CLAIM_ROLE_CODE, String.class));
        loginUser.setStatus(1);
        return loginUser;
    }

    /**
     * 从请求头中提取 token
     */
    public String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader(header);
        if (StringUtils.hasText(bearer) && bearer.startsWith(prefix)) {
            return bearer.substring(prefix.length()).trim();
        }
        return null;
    }

    /**
     * token 过期时间(毫秒时间戳)
     */
    public Date getExpiration(String token) {
        Claims claims = parseToken(token);
        return claims == null ? null : claims.getExpiration();
    }

    /**
     * 配置的 token 有效期(毫秒)
     */
    public Long getExpire() {
        return expire;
    }
}
