package com.insure.ai.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.insure.ai.common.crypto.SensitiveDataCipher;
import com.insure.ai.modules.ai.entity.AiModelConfig;
import com.insure.ai.modules.ai.mapper.AiModelConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 历史密文迁移器
 * <p>
 * 国密改造前 API Key 以 AES-256-GCM 落库。应用启动时扫描一遍配置表,
 * 把无 {@code SM4:} 前缀的历史密文解密后重新用 SM4 加密覆盖,
 * 实现存量数据无感迁移;迁移完成后本类不再产生任何写操作。
 * </p>
 *
 * @author shaoyong.wang
 */
@Slf4j
@Component
@Order(30)
@RequiredArgsConstructor
public class CryptoMigrationRunner implements ApplicationRunner {

    private final AiModelConfigMapper aiModelConfigMapper;
    private final SensitiveDataCipher sensitiveDataCipher;

    @Override
    public void run(ApplicationArguments args) {
        List<AiModelConfig> configs = aiModelConfigMapper.selectList(Wrappers.<AiModelConfig>lambdaQuery()
                .isNotNull(AiModelConfig::getApiKeyCipher));
        int migrated = 0;
        int failed = 0;
        for (AiModelConfig config : configs) {
            String cipherText = config.getApiKeyCipher();
            if (!sensitiveDataCipher.needsUpgrade(cipherText)) {
                continue;
            }
            String plain = sensitiveDataCipher.decrypt(cipherText);
            if (!StringUtils.hasText(plain)) {
                failed++;
                log.warn("[国密迁移] 配置 id={} 的历史密文无法解密,请在后台重新录入 API Key", config.getId());
                continue;
            }
            AiModelConfig update = new AiModelConfig();
            update.setId(config.getId());
            update.setApiKeyCipher(sensitiveDataCipher.encrypt(plain));
            aiModelConfigMapper.updateById(update);
            migrated++;
        }
        if (migrated > 0) {
            log.info("[国密迁移] 已将 {} 条历史 AES 密文升级为 SM4", migrated);
        }
        if (failed > 0) {
            log.warn("[国密迁移] 共 {} 条历史密文迁移失败,需人工处理", failed);
        }
    }
}
