package com.insure.ai.common.crypto;

import lombok.extern.slf4j.Slf4j;

/**
 * 敏感数据加解密统一入口(国密 SM4)
 * <p>
 * 所有需要落库的敏感字段(目前是模型 API Key)统一走本类:
 * </p>
 * <ul>
 *     <li>加密:SM4-GCM,随机 IV,密文带 {@code SM4:} 前缀;</li>
 *     <li>解密:优先 SM4;识别到无前缀的历史密文时按旧 AES 兼容解密,
 *         并提示调用方覆盖写回,实现无感迁移;</li>
 *     <li>掩码:对外展示只保留首 3 位与末 4 位。</li>
 * </ul>
 *
 * @author shaoyong.wang
 */
@Slf4j
public class SensitiveDataCipher {

    /** SM4 密钥(16 字节) */
    private final byte[] sm4Key;

    /** 主密钥口令原文,仅用于兼容解密历史 AES 密文 */
    private final String secret;

    public SensitiveDataCipher(String secret) {
        this.secret = secret;
        this.sm4Key = Sm4Cipher.deriveKey(secret);
    }

    /**
     * 加密
     *
     * @param plainText 明文,为空返回 null
     * @return SM4 密文
     */
    public String encrypt(String plainText) {
        return Sm4Cipher.encrypt(plainText, sm4Key);
    }

    /**
     * 解密,自动兼容历史 AES 密文
     *
     * @param cipherText 密文
     * @return 明文;为空时返回 null
     */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return null;
        }
        if (Sm4Cipher.isSm4Cipher(cipherText)) {
            return Sm4Cipher.decrypt(cipherText, sm4Key);
        }
        String plain = LegacyAesCipher.decrypt(cipherText, secret);
        if (plain != null) {
            log.info("[国密迁移] 检测到历史 AES 密文,已按兼容模式解密,建议尽快重新保存以升级为 SM4");
        }
        return plain;
    }

    /**
     * 是否需要升级为 SM4 密文
     *
     * @param cipherText 密文
     * @return true 表示仍为历史格式
     */
    public boolean needsUpgrade(String cipherText) {
        return LegacyAesCipher.isLegacy(cipherText);
    }

    /**
     * 生成展示掩码
     * <p>例:sk-AbCdEfGhIjKlMnOpQrStUvWxYz012345 → sk-****2345</p>
     *
     * @param plainText 明文
     * @return 掩码
     */
    public String mask(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return null;
        }
        if (plainText.length() <= 8) {
            return "****";
        }
        return plainText.substring(0, 3) + "****" + plainText.substring(plainText.length() - 4);
    }
}
