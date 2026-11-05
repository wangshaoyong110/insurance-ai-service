package com.insure.ai.common.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 国密算法工具单元测试
 * <p>敏感数据加密是安全底线,任何"能加不能解"或"篡改后仍能解密"的回归都必须拦截</p>
 */
@DisplayName("国密算法(SM2/SM3/SM4)")
class SmCryptoTest {

    private static final String SECRET = "unit-test-crypto-secret-please-change";
    /** 测试明文:拼接写法避免被密钥扫描脚本误判为真实 Key */
    private static final String PLAIN = "sk-" + "1234567890abcdefghijklmnopqrstuvwxyz";

    // ==================== SM3 ====================

    @Test
    @DisplayName("SM3 摘要符合标准测试向量")
    void sm3ShouldMatchStandardVector() {
        // GB/T 32905-2016 附录 A 示例:SM3("abc")
        assertThat(Sm3Utils.hashHex("abc"))
                .isEqualTo("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0");
    }

    @Test
    @DisplayName("SM3 十六进制互转可逆")
    void sm3HexShouldRoundTrip() {
        byte[] raw = Sm3Utils.hash(PLAIN);
        assertThat(Sm3Utils.fromHex(Sm3Utils.toHex(raw))).isEqualTo(raw);
        assertThatThrownBy(() -> Sm3Utils.fromHex("abc")).isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== SM4 ====================

    @Test
    @DisplayName("SM4 加解密可逆,且密文带国密前缀")
    void sm4ShouldRoundTrip() {
        byte[] key = Sm4Cipher.deriveKey(SECRET);
        assertThat(key).hasSize(16);

        String cipher = Sm4Cipher.encrypt(PLAIN, key);
        assertThat(cipher).startsWith(Sm4Cipher.PREFIX);
        assertThat(Sm4Cipher.decrypt(cipher, key)).isEqualTo(PLAIN);
    }

    @Test
    @DisplayName("SM4 同一明文两次加密结果不同(随机 IV),但都能解回原文")
    void sm4ShouldUseRandomIv() {
        byte[] key = Sm4Cipher.deriveKey(SECRET);
        String first = Sm4Cipher.encrypt(PLAIN, key);
        String second = Sm4Cipher.encrypt(PLAIN, key);

        assertThat(first).isNotEqualTo(second);
        assertThat(Sm4Cipher.decrypt(first, key)).isEqualTo(PLAIN);
        assertThat(Sm4Cipher.decrypt(second, key)).isEqualTo(PLAIN);
    }

    @Test
    @DisplayName("SM4 密文被篡改时解密失败,不返回错误明文")
    void sm4ShouldRejectTamperedCipher() {
        byte[] key = Sm4Cipher.deriveKey(SECRET);
        String cipher = Sm4Cipher.encrypt(PLAIN, key);
        // 直接改 base64 文本可能只落在填充位、解出同样的字节,这里改解码后的密文字节更可靠
        byte[] raw = Base64.getDecoder().decode(cipher.substring(Sm4Cipher.PREFIX.length()));
        raw[raw.length - 1] ^= 0x01;
        String tampered = Sm4Cipher.PREFIX + Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> Sm4Cipher.decrypt(tampered, key))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("SM4 换密钥后无法解密")
    void sm4ShouldRejectWrongKey() {
        String cipher = Sm4Cipher.encrypt(PLAIN, Sm4Cipher.deriveKey(SECRET));
        byte[] otherKey = Sm4Cipher.deriveKey("another-crypto-secret");

        assertThatThrownBy(() -> Sm4Cipher.decrypt(cipher, otherKey))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("SM4 密钥派生稳定:同口令同密钥,不同口令不同密钥")
    void sm4KeyDerivationShouldBeStable() {
        assertThat(Sm4Cipher.deriveKey(SECRET)).isEqualTo(Sm4Cipher.deriveKey(SECRET));
        assertThat(Sm4Cipher.deriveKey(SECRET)).isNotEqualTo(Sm4Cipher.deriveKey(SECRET + "x"));
    }

    // ==================== SM2 ====================

    @Test
    @DisplayName("SM2 签名验签通过,篡改数据或换密钥均失败")
    void sm2ShouldSignAndVerify() {
        Sm2Utils.KeyPair pair = Sm2Utils.deriveKeyPair(SECRET);
        byte[] data = "header.payload".getBytes(StandardCharsets.US_ASCII);
        byte[] signature = Sm2Utils.sign(data, pair);

        assertThat(signature).isNotEmpty();
        assertThat(Sm2Utils.verify(data, signature, pair)).isTrue();
        assertThat(Sm2Utils.verify("header.payload2".getBytes(StandardCharsets.US_ASCII), signature, pair)).isFalse();
        assertThat(Sm2Utils.verify(data, new byte[0], pair)).isFalse();
        assertThat(Sm2Utils.verify(data, signature, Sm2Utils.deriveKeyPair("another-secret")))
                .isFalse();
    }

    @Test
    @DisplayName("SM2 密钥对由主密钥确定性派生,公钥格式为 04 开头的未压缩点")
    void sm2KeyPairShouldBeDeterministic() {
        Sm2Utils.KeyPair first = Sm2Utils.deriveKeyPair(SECRET);
        Sm2Utils.KeyPair second = Sm2Utils.deriveKeyPair(SECRET);

        assertThat(first.privateKey()).isEqualTo(second.privateKey());
        assertThat(first.publicKeyHex()).isEqualTo(second.publicKeyHex()).startsWith("04");
        assertThat(first.publicKeyHex()).hasSize(130);
        assertThat(Sm2Utils.fingerprint(first)).hasSize(16);
    }

    // ==================== 统一入口与历史兼容 ====================

    @Test
    @DisplayName("SensitiveDataCipher 加解密可逆,并生成掩码")
    void sensitiveCipherShouldWork() {
        SensitiveDataCipher cipher = new SensitiveDataCipher(SECRET);

        String encrypted = cipher.encrypt(PLAIN);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(PLAIN);
        assertThat(cipher.needsUpgrade(encrypted)).isFalse();
        assertThat(cipher.mask(PLAIN)).isEqualTo("sk-****wxyz");
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("历史 AES 密文仍可解密,并标记为待升级")
    void shouldDecryptLegacyAesCipher() throws Exception {
        SensitiveDataCipher cipher = new SensitiveDataCipher(SECRET);
        String legacy = legacyAesEncrypt(PLAIN, SECRET);

        assertThat(cipher.needsUpgrade(legacy)).isTrue();
        assertThat(cipher.decrypt(legacy)).isEqualTo(PLAIN);
    }

    /**
     * 按改造前的算法(AES-256-GCM,密钥 = SHA-256(secret))生成历史密文
     */
    private String legacyAesEncrypt(String plain, String secret) throws Exception {
        byte[] iv = new byte[12];
        byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(128, iv));
        byte[] body = aes.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] raw = new byte[iv.length + body.length];
        System.arraycopy(iv, 0, raw, 0, iv.length);
        System.arraycopy(body, 0, raw, iv.length, body.length);
        return Base64.getEncoder().encodeToString(raw);
    }
}
