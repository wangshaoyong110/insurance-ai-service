package com.insure.ai.config;

import com.insure.ai.common.util.AesCryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;

/**
 * 敏感数据加密配置
 * <p>
 * 主密钥优先从环境变量 INSURE_CRYPTO_SECRET 读取,避免随代码仓库泄露。
 * 若未配置则退化为默认值并打印告警——生产环境必须显式配置,
 * 且主密钥一旦变更,已入库的 API Key 将无法解密。
 * </p>
 *
 * @author insurance-ai
 */
@Slf4j
@Configuration
public class CryptoConfig {

    /** 开发环境兜底密钥,生产必须通过环境变量覆盖 */
    private static final String DEV_FALLBACK_SECRET = "insurance-ai-dev-default-crypto-secret";

    @Value("${insure.crypto.secret:}")
    private String cryptoSecret;

    /**
     * 加密主密钥
     */
    @Bean
    public SecretKey cryptoSecretKey() {
        String secret = cryptoSecret;
        if (secret == null || secret.isBlank()) {
            secret = DEV_FALLBACK_SECRET;
            log.warn("[安全告警] 未配置 insure.crypto.secret,正在使用内置默认密钥。"
                    + "生产环境请设置环境变量 INSURE_CRYPTO_SECRET,否则 API Key 加密形同虚设");
        } else if (DEV_FALLBACK_SECRET.equals(secret)) {
            log.warn("[安全告警] insure.crypto.secret 仍为默认值,生产环境请更换");
        }
        return AesCryptoUtil.deriveKey(secret);
    }
}
