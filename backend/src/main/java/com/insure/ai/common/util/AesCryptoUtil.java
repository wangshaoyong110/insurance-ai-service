package com.insure.ai.common.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加解密工具
 * <p>
 * 用于 API Key 等敏感配置的落库加密。GCM 模式自带完整性校验,
 * 密文被篡改时解密会直接失败,不会返回错误明文。
 * </p>
 * <p>
 * 密文结构:Base64( IV(12字节) || 密文 || 认证标签(16字节) )
 * 每次加密使用随机 IV,因此相同明文每次加密结果不同。
 * </p>
 *
 * @author insurance-ai
 */
public final class AesCryptoUtil {

    /** 加密算法:GCM 无需填充 */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** IV 长度,GCM 推荐 12 字节 */
    private static final int IV_LENGTH = 12;

    /** 认证标签长度(位) */
    private static final int TAG_LENGTH_BIT = 128;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesCryptoUtil() {
    }

    /**
     * 由口令派生 256 位密钥
     * <p>使用 SHA-256 摘要保证任意长度口令都能得到合法的 32 字节密钥</p>
     *
     * @param secret 主密钥口令
     * @return AES 密钥
     */
    public static SecretKey deriveKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("加密主密钥不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("生成加密密钥失败", e);
        }
    }

    /**
     * 加密
     *
     * @param plainText 明文,为空时返回 null
     * @param secretKey 密钥
     * @return Base64 密文
     */
    public static String encrypt(String plainText, SecretKey secretKey) {
        if (plainText == null || plainText.isEmpty()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(cipherBytes, 0, result, iv.length, cipherBytes.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    /**
     * 解密
     *
     * @param cipherText Base64 密文,为空时返回 null
     * @param secretKey  密钥
     * @return 明文
     */
    public static String decrypt(String cipherText, SecretKey secretKey) {
        if (cipherText == null || cipherText.isEmpty()) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(cipherText);
            if (raw.length <= IV_LENGTH) {
                throw new IllegalArgumentException("密文长度不合法");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(raw, 0, iv, 0, IV_LENGTH);
            byte[] cipherBytes = new byte[raw.length - IV_LENGTH];
            System.arraycopy(raw, IV_LENGTH, cipherBytes, 0, cipherBytes.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("解密失败,主密钥可能已变更", e);
        }
    }

    /**
     * 生成用于界面展示的掩码,保留前 3 位与后 4 位
     * <p>例:sk-AbCdEfGhIjKlMnOpQrStUvWxYz012345 → sk-****2345</p>
     *
     * @param plainText 明文 Key
     * @return 掩码
     */
    public static String mask(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return null;
        }
        if (plainText.length() <= 8) {
            return "****";
        }
        return plainText.substring(0, 3) + "****" + plainText.substring(plainText.length() - 4);
    }
}
