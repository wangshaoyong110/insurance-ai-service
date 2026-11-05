package com.insure.ai.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insure.ai.common.crypto.Sm2Utils;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT 工具类:负责 token 的生成、解析与校验
 * <p>
 * 签名算法为 <b>SM2-SM3</b>(国密椭圆曲线签名 + 国密摘要),替换原 HMAC-SHA256 方案:
 * 服务端持有 SM2 私钥签名,验签只需公钥,不必在各服务间共享对称密钥。
 * 密钥对由 {@code insure.jwt.secret} 确定性派生,重启后历史 token 仍有效。
 * </p>
 * <p>
 * 报文结构保持标准 JWT 三段式:Base64URL(Header) + "." + Base64URL(Payload) + "." + Base64URL(Signature)。
 * </p>
 *
 * @author shaoyong.wang
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

    /** JOSE 头:算法声明为 SM2-SM3,便于网关识别 */
    private static final String JOSE_HEADER = "{\"alg\":\"" + Sm2Utils.ALGORITHM + "\",\"typ\":\"JWT\"}";

    /** 头与载荷段按 ASCII 参与签名,避免字符集差异导致验签失败 */
    private static final java.nio.charset.Charset ASCII = StandardCharsets.US_ASCII;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${insure.jwt.secret}")
    private String secret;

    @Value("${insure.jwt.expire}")
    private Long expire;

    @Value("${insure.jwt.prefix}")
    private String prefix;

    @Value("${insure.jwt.header}")
    private String header;

    /** SM2 签名密钥对 */
    private Sm2Utils.KeyPair keyPair;

    /** 编码后的 Header,避免每次签发重复计算 */
    private String encodedHeader;

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(secret) || secret.length() < 32) {
            // 主密钥过短会显著降低签名安全强度,启动即失败优于带病运行
            throw new IllegalStateException("insure.jwt.secret 长度必须不少于 32 个字符");
        }
        this.keyPair = Sm2Utils.deriveKeyPair(secret);
        this.encodedHeader = Sm2Utils.base64UrlEncode(JOSE_HEADER.getBytes(ASCII));
        log.info("[JWT] 签名算法={} 公钥指纹={}", Sm2Utils.ALGORITHM, Sm2Utils.fingerprint(keyPair));
    }

    /**
     * 生成 token
     *
     * @param loginUser 登录用户
     * @return 三段式 token
     */
    public String generateToken(LoginUser loginUser) {
        long nowSeconds = System.currentTimeMillis() / 1000;
        Map<String, Object> payload = new LinkedHashMap<>(10);
        payload.put("sub", loginUser.getUsername());
        putIfPresent(payload, CLAIM_USER_ID, loginUser.getId());
        putIfPresent(payload, CLAIM_USER_TYPE, loginUser.getUserType());
        putIfPresent(payload, CLAIM_REAL_NAME, loginUser.getRealName());
        putIfPresent(payload, CLAIM_PHONE, loginUser.getPhone());
        putIfPresent(payload, CLAIM_ROLE_CODE, loginUser.getRoleCode());
        payload.put("iat", nowSeconds);
        payload.put("exp", nowSeconds + Math.max(expire, 0L) / 1000);

        String body;
        try {
            body = Sm2Utils.base64UrlEncode(MAPPER.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new IllegalStateException("生成 token 载荷失败", e);
        }
        byte[] signature = Sm2Utils.sign((encodedHeader + "." + body).getBytes(ASCII), keyPair);
        return encodedHeader + "." + body + "." + Sm2Utils.base64UrlEncode(signature);
    }

    /**
     * 解析并验签,失败返回 null(不抛异常,避免被构造异常流量放大)
     *
     * @param token 三段式 token
     * @return 载荷,校验不通过时返回 null
     */
    public JwtPayload parseToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            byte[] signature = Sm2Utils.base64UrlDecode(parts[2]);
            byte[] signed = (parts[0] + "." + parts[1]).getBytes(ASCII);
            if (!Sm2Utils.verify(signed, signature, keyPair)) {
                log.debug("[JWT] 验签失败");
                return null;
            }
            Map<String, Object> claims = MAPPER.readValue(Sm2Utils.base64UrlDecode(parts[1]),
                    new TypeReference<Map<String, Object>>() {
                    });
            long expiresAt = asLong(claims.get("exp"), 0L);
            if (expiresAt * 1000 <= System.currentTimeMillis()) {
                log.debug("[JWT] token 已过期");
                return null;
            }
            return new JwtPayload(
                    asString(claims.get("sub")),
                    asLong(claims.get(CLAIM_USER_ID), null),
                    asString(claims.get(CLAIM_USER_TYPE)),
                    asString(claims.get(CLAIM_REAL_NAME)),
                    asString(claims.get(CLAIM_PHONE)),
                    asString(claims.get(CLAIM_ROLE_CODE)),
                    asLong(claims.get("iat"), 0L),
                    expiresAt);
        } catch (Exception e) {
            log.debug("[JWT] token 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 由 token 还原登录用户
     *
     * @param token 三段式 token
     * @return 登录用户,校验不通过时返回 null
     */
    public LoginUser toLoginUser(String token) {
        JwtPayload payload = parseToken(token);
        if (payload == null) {
            return null;
        }
        LoginUser loginUser = new LoginUser();
        loginUser.setId(payload.userId());
        loginUser.setUsername(payload.subject());
        loginUser.setUserType(payload.userType());
        loginUser.setRealName(payload.realName());
        loginUser.setPhone(payload.phone());
        loginUser.setRoleCode(payload.roleCode());
        loginUser.setStatus(1);
        return loginUser;
    }

    /**
     * 从请求头中提取 token
     *
     * @param request 当前请求
     * @return 裸 token,不符合约定时返回 null
     */
    public String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader(header);
        if (StringUtils.hasText(bearer) && bearer.startsWith(prefix)) {
            return bearer.substring(prefix.length()).trim();
        }
        return null;
    }

    /**
     * token 过期时间
     *
     * @param token 三段式 token
     * @return 过期时间,解析失败返回 null
     */
    public Date getExpiration(String token) {
        JwtPayload payload = parseToken(token);
        return payload == null ? null : new Date(payload.expiresAt() * 1000);
    }

    /**
     * 配置的 token 有效期(毫秒)
     *
     * @return 有效期
     */
    public Long getExpire() {
        return expire;
    }

    /**
     * 当前签名公钥(十六进制,04 开头未压缩格式)
     * <p>供网关或其他服务配置验签公钥使用,不涉及私钥泄露</p>
     *
     * @return 公钥十六进制串
     */
    public String getPublicKeyHex() {
        return keyPair.publicKeyHex();
    }

    /**
     * 公钥指纹(SM3 摘要前 16 位),便于核对密钥版本
     *
     * @return 指纹
     */
    public String getKeyFingerprint() {
        return Sm2Utils.fingerprint(keyPair);
    }

    private static void putIfPresent(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long asLong(Object value, Long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * token 载荷
     *
     * @param subject   登录账号
     * @param userId    用户 ID
     * @param userType  用户类型(ADMIN / CUSTOMER)
     * @param realName  真实姓名
     * @param phone     手机号
     * @param roleCode  角色标识
     * @param issuedAt  签发时间(秒)
     * @param expiresAt 过期时间(秒)
     */
    public record JwtPayload(String subject, Long userId, String userType, String realName,
                             String phone, String roleCode, long issuedAt, long expiresAt) {
    }
}
