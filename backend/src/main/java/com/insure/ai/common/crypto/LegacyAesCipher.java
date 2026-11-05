package com.insure.ai.common.crypto;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 历史 AES-256-GCM 密文兼容解密器(仅用于存量数据迁移)
 * <p>
 * 国密改造前,API Key 使用 AES-256-GCM 加密落库。为了不要求运维先清空配置表,
 * 该类保留旧算法用于一次性解密,解密成功后由 {@link SensitiveDataCipher}
 * 重新以 SM4 加密覆盖。存量数据迁移完成后本类可整体删除。
 * </p>
 * <p><b>禁止在新代码中调用本类做加密</b>,新数据一律使用 SM4。</p>
 *
 * @author shaoyong.wang
 * @deprecated 历史数据迁移专用,迁移完成后删除
 */
@Deprecated(since = "1.1.0", forRemoval = true)
@Slf4j
final class LegacyAesCipher {

    /** 旧算法:与改造前保持一致 */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** 旧格式 IV 长度 */
    private static final int IV_LENGTH = 12;

    /** 认证标签长度(位) */
    private static final int TAG_LENGTH_BIT = 128;

    private LegacyAesCipher() {
    }

    /**
     * 判断是否为旧格式密文(无 SM4 前缀)
     *
     * @param cipherText 密文
     * @return 是否需要走旧算法解密
     */
    static boolean isLegacy(String cipherText) {
        return cipherText != null && !cipherText.isEmpty() && !Sm4Cipher.isSm4Cipher(cipherText);
    }

    /**
     * 旧算法解密
     *
     * @param cipherText Base64 密文(IV || 密文 || 标签)
     * @param secret     主密钥口令
     * @return 明文,无法解密时返回 null
     */
    static String decrypt(String cipherText, String secret) {
        try {
            byte[] raw = Base64.getDecoder().decode(cipherText);
            if (raw.length <= IV_LENGTH) {
                return null;
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(raw, 0, iv, 0, IV_LENGTH);
            byte[] body = new byte[raw.length - IV_LENGTH];
            System.arraycopy(raw, IV_LENGTH, body, 0, body.length);
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[国密迁移] 旧 AES 密文解密失败,可能需要人工重置: {}", e.getMessage());
            return null;
        }
    }
}
