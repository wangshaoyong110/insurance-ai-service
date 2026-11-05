package com.insure.ai.common.crypto;

import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.digests.SM3Digest;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * SM4-GCM 对称加解密工具(国密分组密码,GB/T 32907-2016)
 * <p>
 * 用于 API Key 等敏感配置的落库加密,替换原 AES-256-GCM 方案。
 * 选择 GCM 认证加密模式,密文被篡改时解密直接失败,不会返回错误明文;
 * 每次加密使用随机 IV,相同明文每次密文不同。
 * </p>
 * <p>
 * 密文结构:{@code SM4:} + Base64( IV(12字节) || 密文 || 认证标签(16字节) )。
 * 前缀用于区分国密密文与历史 AES 密文,便于灰度迁移。
 * </p>
 *
 * @author shaoyong.wang
 */
public final class Sm4Cipher {

    /** 密文标识前缀 */
    public static final String PREFIX = "SM4:";

    /** 算法标识,便于日志追溯 */
    public static final String ALGORITHM = "SM4/GCM/NoPadding";

    /** 密钥长度:SM4 分组长度固定 128 位 */
    private static final int KEY_BYTES = 16;

    /** GCM 推荐 IV 长度 */
    private static final int IV_LENGTH = 12;

    /** 认证标签长度(位) */
    private static final int TAG_LENGTH_BIT = 128;

    /** 口令派生迭代次数 */
    private static final int KDF_ITERATIONS = 10_000;

    /** 口令派生固定盐值:保证同一主密钥每次派生出同一把 SM4 密钥 */
    private static final byte[] KDF_SALT = "insure-ai-sm4-kdf-salt".getBytes(StandardCharsets.UTF_8);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private Sm4Cipher() {
    }

    /**
     * 由主密钥口令派生 SM4 密钥
     * <p>采用 PBKDF2 + HMAC-SM3 派生,比直接截断摘要更能抵抗字典攻击</p>
     *
     * @param secret 主密钥口令
     * @return 16 字节 SM4 密钥
     */
    public static byte[] deriveKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("加密主密钥不能为空");
        }
        PKCS5S2ParametersGenerator generator = new PKCS5S2ParametersGenerator(new SM3Digest());
        generator.init(secret.getBytes(StandardCharsets.UTF_8), KDF_SALT, KDF_ITERATIONS);
        KeyParameter keyParameter = (KeyParameter) generator.generateDerivedParameters(KEY_BYTES * 8);
        return keyParameter.getKey();
    }

    /**
     * 加密
     *
     * @param plainText 明文,为空时返回 null
     * @param key       SM4 密钥
     * @return 带 SM4 前缀的 Base64 密文
     */
    public static String encrypt(String plainText, byte[] key) {
        if (plainText == null || plainText.isEmpty()) {
            return null;
        }
        byte[] iv = new byte[IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        byte[] plain = plainText.getBytes(StandardCharsets.UTF_8);
        byte[] sealed = process(true, plain, key, iv);
        byte[] result = new byte[iv.length + sealed.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(sealed, 0, result, iv.length, sealed.length);
        return PREFIX + Base64.getEncoder().encodeToString(result);
    }

    /**
     * 解密
     *
     * @param cipherText 带 SM4 前缀的 Base64 密文,为空时返回 null
     * @param key        SM4 密钥
     * @return 明文
     */
    public static String decrypt(String cipherText, byte[] key) {
        if (cipherText == null || cipherText.isEmpty()) {
            return null;
        }
        if (!isSm4Cipher(cipherText)) {
            throw new IllegalArgumentException("密文格式不合法:缺少 " + PREFIX + " 前缀");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(cipherText.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("解密失败:密文不是合法的 Base64", e);
        }
        if (raw.length <= IV_LENGTH) {
            throw new IllegalStateException("解密失败:密文长度不合法");
        }
        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(raw, 0, iv, 0, IV_LENGTH);
        byte[] sealed = new byte[raw.length - IV_LENGTH];
        System.arraycopy(raw, IV_LENGTH, sealed, 0, sealed.length);
        try {
            return new String(process(false, sealed, key, iv), StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("解密失败,主密钥可能已变更或密文被篡改", e);
        }
    }

    /**
     * 是否为 SM4 密文
     *
     * @param text 待判断文本
     * @return 是否带 SM4 前缀
     */
    public static boolean isSm4Cipher(String text) {
        return text != null && text.startsWith(PREFIX);
    }

    /**
     * GCM 加解密统一入口
     */
    private static byte[] process(boolean encrypt, byte[] input, byte[] key, byte[] iv) {
        GCMBlockCipher cipher = new GCMBlockCipher(new SM4Engine());
        AEADParameters parameters = new AEADParameters(new KeyParameter(key), TAG_LENGTH_BIT, iv);
        cipher.init(encrypt, parameters);
        byte[] out = new byte[cipher.getOutputSize(input.length)];
        int length = cipher.processBytes(input, 0, input.length, out, 0);
        try {
            length += cipher.doFinal(out, length);
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException(encrypt ? "加密失败" : "解密失败", e);
        }
        if (length == out.length) {
            return out;
        }
        byte[] trimmed = new byte[length];
        System.arraycopy(out, 0, trimmed, 0, length);
        return trimmed;
    }
}
