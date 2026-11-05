package com.insure.ai.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.insure.ai.common.enums.AiProviderEnum;
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

import java.math.BigDecimal;

/**
 * 模型配置初始化
 * <p>
 * 首次启动(配置表为空)时,把 application.yml 中的 DeepSeek 配置迁移进数据库,
 * API Key 加密后落库,并标记为默认配置。之后所有模型参数都可在后台维护,
 * 不再依赖重启应用。
 * </p>
 *
 * @author insurance-ai
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class AiModelInitializer implements ApplicationRunner {

    private final AiModelConfigMapper aiModelConfigMapper;
    private final DeepSeekProperties deepSeekProperties;
    private final SensitiveDataCipher sensitiveDataCipher;

    @Override
    public void run(ApplicationArguments args) {
        Long exists = aiModelConfigMapper.selectCount(Wrappers.emptyWrapper());
        if (exists != null && exists > 0) {
            return;
        }
        if (!StringUtils.hasText(deepSeekProperties.getApiKey())) {
            log.warn("[模型配置] 配置表为空且未提供 DeepSeek API Key,请登录后台在【系统管理-模型配置】中手动添加");
            return;
        }
        AiModelConfig config = new AiModelConfig();
        config.setConfigName("DeepSeek-默认");
        config.setProvider(AiProviderEnum.OPENAI_COMPATIBLE.getCode());
        config.setBaseUrl(StringUtils.hasText(deepSeekProperties.getBaseUrl())
                ? deepSeekProperties.getBaseUrl() : "https://api.deepseek.com");
        config.setApiKeyCipher(sensitiveDataCipher.encrypt(deepSeekProperties.getApiKey()));
        config.setApiKeyMask(sensitiveDataCipher.mask(deepSeekProperties.getApiKey()));
        config.setModel(deepSeekProperties.getModel());
        config.setTemperature(deepSeekProperties.getTemperature() == null
                ? new BigDecimal("0.30") : BigDecimal.valueOf(deepSeekProperties.getTemperature()));
        config.setMaxTokens(1024);
        config.setTimeoutMs(deepSeekProperties.getTimeout() == null ? 60_000 : deepSeekProperties.getTimeout());
        config.setIsDefault(1);
        config.setStatus(1);
        config.setRemark("由 application.yml 的 insure.deepseek 配置自动迁移,可在后台修改");
        aiModelConfigMapper.insert(config);
        log.info("[模型配置] 已完成首次迁移: id={} provider={} model={} baseUrl={} keyMask={}",
                config.getId(), config.getProvider(), config.getModel(), config.getBaseUrl(),
                config.getApiKeyMask());
    }
}
