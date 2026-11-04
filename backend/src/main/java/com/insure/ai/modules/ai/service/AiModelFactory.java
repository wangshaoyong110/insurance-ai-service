package com.insure.ai.modules.ai.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.AiProviderEnum;
import com.insure.ai.common.util.AesCryptoUtil;
import com.insure.ai.modules.ai.entity.AiModelConfig;
import com.insure.ai.modules.ai.mapper.AiModelConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大模型工厂
 * <p>
 * 按数据库中的配置在运行时构建 {@link ChatModel},支持同一套代码切换
 * 通义千问(DashScope)与任意 OpenAI 兼容服务(DeepSeek / vLLM / Ollama / 内网网关)。
 * </p>
 * <p>
 * 构建出的模型实例按配置 ID 缓存,配置更新(update_time 变化)后自动重建,
 * 因此后台改完配置无需重启应用。
 * </p>
 *
 * @author insurance-ai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelFactory {

    /** 建连超时(毫秒) */
    private static final int CONNECT_TIMEOUT_MS = 10_000;

    /** 连通性测试使用的提示语 */
    private static final String PROBE_PROMPT = "请回复两个字:你好";

    private final AiModelConfigMapper aiModelConfigMapper;
    private final SecretKey cryptoSecretKey;

    /** 配置ID -> 已构建的模型实例 */
    private final Map<Long, CachedModel> modelCache = new ConcurrentHashMap<>();

    /**
     * 缓存项:记录构建时的配置更新时间,用于判断是否需要重建
     */
    private record CachedModel(LocalDateTime updateTime, ChatModel chatModel) {
    }

    /**
     * 获取默认模型
     * <p>优先取「默认配置 + 启用」,其次取任一启用配置</p>
     */
    public ChatModel defaultModel() {
        return toModel(defaultConfig());
    }

    /**
     * 获取默认配置对应的 ChatClient
     */
    public ChatClient defaultChatClient() {
        return ChatClient.builder(defaultModel()).build();
    }

    /**
     * 获取默认配置实体(用于读取模型名、温度等元信息)
     */
    public AiModelConfig defaultConfig() {
        List<AiModelConfig> defaults = aiModelConfigMapper.selectList(Wrappers.<AiModelConfig>lambdaQuery()
                .eq(AiModelConfig::getIsDefault, 1)
                .eq(AiModelConfig::getStatus, 1)
                .orderByDesc(AiModelConfig::getId)
                .last("LIMIT 1"));
        if (!defaults.isEmpty()) {
            return defaults.get(0);
        }
        List<AiModelConfig> enabled = aiModelConfigMapper.selectList(Wrappers.<AiModelConfig>lambdaQuery()
                .eq(AiModelConfig::getStatus, 1)
                .orderByDesc(AiModelConfig::getId)
                .last("LIMIT 1"));
        if (enabled.isEmpty()) {
            throw new BusinessException(ResultCode.AI_MODEL_NOT_CONFIGURED);
        }
        log.warn("[模型配置] 未指定默认配置,回退使用 id={} 的启用配置", enabled.get(0).getId());
        return enabled.get(0);
    }

    /**
     * 按配置实体获取模型(内部使用,含缓存)
     */
    public ChatModel toModel(AiModelConfig config) {
        CachedModel cached = modelCache.get(config.getId());
        if (cached != null && cached.updateTime() != null
                && cached.updateTime().equals(config.getUpdateTime())) {
            return cached.chatModel();
        }
        ChatModel chatModel = build(config, decryptApiKey(config));
        modelCache.put(config.getId(), new CachedModel(config.getUpdateTime(), chatModel));
        log.info("[模型配置] 已构建模型实例 id={} name={} provider={} model={}",
                config.getId(), config.getConfigName(), config.getProvider(), config.getModel());
        return chatModel;
    }

    /**
     * 使用指定配置做一次真实调用,验证地址与 Key 是否可用
     *
     * @param config 配置实体
     * @param apiKey 明文 Key
     * @return 模型回复内容
     */
    public String probe(AiModelConfig config, String apiKey) {
        ChatModel chatModel = build(config, apiKey);
        long start = System.currentTimeMillis();
        String content = ChatClient.builder(chatModel).build()
                .prompt()
                .user(PROBE_PROMPT)
                .call()
                .content();
        log.info("[模型配置] 连通性测试通过 config={} 耗时={}ms", config.getConfigName(),
                System.currentTimeMillis() - start);
        return content;
    }

    /**
     * 解密配置中的 API Key
     */
    public String decryptApiKey(AiModelConfig config) {
        return AesCryptoUtil.decrypt(config.getApiKeyCipher(), cryptoSecretKey);
    }

    /**
     * 清除指定配置的模型缓存(配置变更后调用)
     */
    public void evict(Long configId) {
        modelCache.remove(configId);
    }

    /**
     * 构建 ChatModel
     */
    private ChatModel build(AiModelConfig config, String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ResultCode.AI_MODEL_NOT_CONFIGURED, "该配置缺少 API Key");
        }
        AiProviderEnum provider = AiProviderEnum.of(config.getProvider());
        String baseUrl = StringUtils.hasText(config.getBaseUrl())
                ? config.getBaseUrl().trim() : provider.getDefaultBaseUrl();
        int timeoutMs = config.getTimeoutMs() == null ? 60_000 : config.getTimeoutMs();
        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory(timeoutMs));

        Double temperature = toDouble(config.getTemperature());
        int maxTokens = config.getMaxTokens() == null ? 1024 : config.getMaxTokens();

        if (provider == AiProviderEnum.DASHSCOPE) {
            DashScopeApi api = DashScopeApi.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .restClientBuilder(restClientBuilder)
                    .build();
            DashScopeChatOptions.DashscopeChatOptionsBuilder dsBuilder = DashScopeChatOptions.builder()
                    .withModel(config.getModel())
                    .withMaxToken(maxTokens);
            if (temperature != null) {
                dsBuilder.withTemperature(temperature);
            }
            return DashScopeChatModel.builder()
                    .dashScopeApi(api)
                    .defaultOptions(dsBuilder.build())
                    .build();
        }

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder)
                .build();
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(config.getModel())
                .maxTokens(maxTokens);
        if (temperature != null) {
            optionsBuilder.temperature(temperature);
        }
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(optionsBuilder.build())
                .build();
    }

    /**
     * 带超时的请求工厂
     * <p>对话类请求耗时较长,读超时必须可配,否则默认超时会截断长回答</p>
     */
    private SimpleClientHttpRequestFactory requestFactory(int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return factory;
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
