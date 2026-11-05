package com.insure.ai.common.crypto;

import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * SM3 摘要工具(国密杂凑算法,GB/T 32905-2016)
 * <p>
 * 输出长度 256 位,安全强度与 SHA-256 相当,用于替换 SHA-256 / HMAC-SHA256。
 * 全部使用 BouncyCastle 轻量级 API,不依赖 JCE Provider 注册,避免与其他
 * Provider(如某些中间件自带的 BC 版本)产生冲突。
 * </p>
 *
 * @author shaoyong.wang
 */
public final class Sm3Utils {

    /** 十六进制字符表 */
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Sm3Utils() {
    }

    /**
     * SM3 摘要
     *
     * @param data 原始字节
     * @return 32 字节摘要
     */
    public static byte[] hash(byte[] data) {
        SM3Digest digest = new SM3Digest();
        digest.update(data, 0, data.length);
        byte[] out = new byte[digest.getDigestSize()];
        digest.doFinal(out, 0);
        return out;
    }

    /**
     * 字符串 SM3 摘要(UTF-8)
     *
     * @param text 明文
     * @return 32 字节摘要
     */
    public static byte[] hash(String text) {
        return hash(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * SM3 摘要的十六进制表示,便于日志与比对
     *
     * @param text 明文
     * @return 64 位小写十六进制串
     */
    public static String hashHex(String text) {
        return toHex(hash(text));
    }

    /**
     * HMAC-SM3(以 SM3 为底层杂凑的带密钥消息认证码)
     *
     * @param key  密钥
     * @param data 消息
     * @return 32 字节认证码
     */
    public static byte[] hmac(byte[] key, byte[] data) {
        HMac mac = new HMac(new SM3Digest());
        mac.init(new KeyParameter(key));
        mac.update(data, 0, data.length);
        byte[] out = new byte[mac.getMacSize()];
        mac.doFinal(out, 0);
        return out;
    }

    /**
     * 定长时间比较,避免通过比较耗时侧信道推断摘要内容
     *
     * @param left  字节数组
     * @param right 字节数组
     * @return 是否完全一致
     */
    public static boolean constantTimeEquals(byte[] left, byte[] right) {
        if (left == null || right == null) {
            return left == right;
        }
        return MessageDigest.isEqual(left, right);
    }

    /**
     * 字节数组转小写十六进制
     *
     * @param bytes 字节数组
     * @return 十六进制串
     */
    public static String toHex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            chars[i * 2] = HEX[value >>> 4];
            chars[i * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(chars);
    }

    /**
     * 十六进制串转字节数组
     *
     * @param hex 十六进制串
     * @return 字节数组
     */
    public static byte[] fromHex(String hex) {
        if (hex == null) {
            return new byte[0];
        }
        String normalized = hex.trim();
        if ((normalized.length() & 1) != 0) {
            throw new IllegalArgumentException("十六进制串长度必须为偶数");
        }
        byte[] out = new byte[normalized.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(normalized.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
