package com.insure.ai.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * API Key 加解密单元测试
 * <p>覆盖三件事：能往返、密文每次不同（随机 IV）、换密钥必须失败而不是返回乱码</p>
 */
@DisplayName("AES-256-GCM 加解密")
class AesCryptoUtilTest {

    private static final String PLAIN = "sk-fake-unit-test-key-0123456789";

    private final SecretKey key = AesCryptoUtil.deriveKey("unit-test-secret-key-please-change");

    @Test
    @DisplayName("加密后能原样解回")
    void shouldRoundTrip() {
        String cipher = AesCryptoUtil.encrypt(PLAIN, key);

        assertThat(cipher).isNotEqualTo(PLAIN);
        assertThat(AesCryptoUtil.decrypt(cipher, key)).isEqualTo(PLAIN);
    }

    @Test
    @DisplayName("相同明文两次加密结果不同（IV 随机），避免密文可比对")
    void shouldProduceDifferentCipherEachTime() {
        String first = AesCryptoUtil.encrypt(PLAIN, key);
        String second = AesCryptoUtil.encrypt(PLAIN, key);

        assertThat(first).isNotEqualTo(second);
        assertThat(AesCryptoUtil.decrypt(first, key)).isEqualTo(PLAIN);
        assertThat(AesCryptoUtil.decrypt(second, key)).isEqualTo(PLAIN);
    }

    @Test
    @DisplayName("换主密钥后解密必须抛异常，不能返回错误数据")
    void shouldFailWithWrongKey() {
        String cipher = AesCryptoUtil.encrypt(PLAIN, key);
        SecretKey otherKey = AesCryptoUtil.deriveKey("a-totally-different-secret");

        assertThatThrownBy(() -> AesCryptoUtil.decrypt(cipher, otherKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("主密钥可能已变更");
    }

    @Test
    @DisplayName("密文被篡改后解密失败（GCM 完整性校验）")
    void shouldRejectTamperedCipher() {
        String cipher = AesCryptoUtil.encrypt(PLAIN, key);
        String tampered = cipher.substring(0, cipher.length() - 4) + "AAAA";

        assertThatThrownBy(() -> AesCryptoUtil.decrypt(tampered, key))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("空密文返回 null，不抛异常")
    void shouldReturnNullForBlankCipher() {
        assertThat(AesCryptoUtil.decrypt(null, key)).isNull();
        assertThat(AesCryptoUtil.decrypt("", key)).isNull();
    }

    @Test
    @DisplayName("同样的 secret 派生出的密钥一致，不同 secret 派生出不同密钥")
    void shouldDeriveStableKey() {
        assertThat(AesCryptoUtil.deriveKey("same-secret"))
                .isEqualTo(AesCryptoUtil.deriveKey("same-secret"));
        assertThat(AesCryptoUtil.deriveKey("secret-a"))
                .isNotEqualTo(AesCryptoUtil.deriveKey("secret-b"));
    }

    @Test
    @DisplayName("展示掩码只保留前 3 位与后 4 位")
    void shouldMaskApiKey() {
        assertThat(AesCryptoUtil.mask("sk-fake-unit-test-key-0123456789")).isEqualTo("sk-****6789");
        assertThat(AesCryptoUtil.mask("short")).isEqualTo("****");
        assertThat(AesCryptoUtil.mask(null)).isNull();
    }
}
