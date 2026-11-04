package com.insure.ai.modules.ai.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.util.AesCryptoUtil;
import com.insure.ai.modules.ai.dto.AiModelSaveDTO;
import com.insure.ai.modules.ai.dto.AiModelTestDTO;
import com.insure.ai.modules.ai.entity.AiModelConfig;
import com.insure.ai.modules.ai.mapper.AiModelConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大模型配置服务
 * <p>API Key 一律以 AES-256-GCM 密文入库,对外只暴露掩码</p>
 *
 * @author insurance-ai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelConfigService {

    /** 默认采样温度:保险场景需要严谨表述,取值偏低 */
    private static final BigDecimal DEFAULT_TEMPERATURE = new BigDecimal("0.30");

    /** 默认最大 token 数 */
    private static final int DEFAULT_MAX_TOKENS = 1024;

    /** 默认读超时(毫秒) */
    private static final int DEFAULT_TIMEOUT_MS = 60_000;

    private final AiModelConfigMapper aiModelConfigMapper;
    private final AiModelFactory aiModelFactory;
    private final SecretKey cryptoSecretKey;

    /**
     * 配置列表(按默认优先、创建时间倒序)
     */
    public List<AiModelConfig> list() {
        return aiModelConfigMapper.selectList(Wrappers.<AiModelConfig>lambdaQuery()
                .orderByDesc(AiModelConfig::getIsDefault)
                .orderByDesc(AiModelConfig::getId));
    }

    /**
     * 新增或编辑配置
     */
    @Transactional(rollbackFor = Exception.class)
    public Long save(AiModelSaveDTO dto) {
        AiModelConfig entity;
        boolean creating = dto.getId() == null;
        if (creating) {
            entity = new AiModelConfig();
        } else {
            entity = aiModelConfigMapper.selectById(dto.getId());
            if (entity == null) {
                throw new BusinessException(ResultCode.AI_MODEL_NOT_EXIST);
            }
        }
        BeanUtils.copyProperties(dto, entity, "apiKey");
        entity.setId(dto.getId());
        applyDefaults(entity);

        // API Key:新增必填;编辑留空表示保持原值
        if (StringUtils.hasText(dto.getApiKey())) {
            String plainKey = dto.getApiKey().trim();
            entity.setApiKeyCipher(AesCryptoUtil.encrypt(plainKey, cryptoSecretKey));
            entity.setApiKeyMask(AesCryptoUtil.mask(plainKey));
        } else if (creating) {
            throw new BusinessException(ResultCode.AI_MODEL_TEST_FAILED, "请输入 API Key");
        }

        if (creating) {
            aiModelConfigMapper.insert(entity);
        } else {
            aiModelConfigMapper.updateById(entity);
        }

        // 同一时刻只允许一个默认配置
        if (Integer.valueOf(1).equals(entity.getIsDefault())) {
            clearOtherDefault(entity.getId());
        } else if (!creating && isLastEnabled(entity.getId())) {
            entity.setIsDefault(1);
            aiModelConfigMapper.updateById(entity);
        }

        aiModelFactory.evict(entity.getId());
        log.info("[模型配置] 保存成功 id={} name={} provider={} model={}",
                entity.getId(), entity.getConfigName(), entity.getProvider(), entity.getModel());
        return entity.getId();
    }

    /**
     * 删除配置(逻辑删除)
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AiModelConfig entity = requireConfig(id);
        aiModelConfigMapper.deleteById(id);
        aiModelFactory.evict(id);
        log.info("[模型配置] 已删除 id={} name={}", id, entity.getConfigName());
        ensureDefaultExists();
    }

    /**
     * 启用 / 停用
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        AiModelConfig entity = requireConfig(id);
        entity.setStatus(status);
        aiModelConfigMapper.updateById(entity);
        aiModelFactory.evict(id);
        if (Integer.valueOf(0).equals(status) && Integer.valueOf(1).equals(entity.getIsDefault())) {
            // 停用默认配置后需要重新指定默认,否则对话将无模型可用
            entity.setIsDefault(0);
            aiModelConfigMapper.updateById(entity);
            ensureDefaultExists();
        }
        log.info("[模型配置] 状态变更 id={} status={}", id, status);
    }

    /**
     * 设为默认配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        AiModelConfig entity = requireConfig(id);
        if (!Integer.valueOf(1).equals(entity.getStatus())) {
            throw new BusinessException(ResultCode.AI_MODEL_DISABLED, "配置已停用,请先启用再设为默认");
        }
        entity.setIsDefault(1);
        aiModelConfigMapper.updateById(entity);
        clearOtherDefault(id);
        log.info("[模型配置] 已设为默认 id={} name={}", id, entity.getConfigName());
    }

    /**
     * 连通性测试
     *
     * @return 含耗时与模型回复的结果
     */
    public Map<String, Object> test(AiModelTestDTO dto) {
        AiModelConfig config;
        String apiKey;
        if (dto.getId() != null) {
            config = requireConfig(dto.getId());
            if (StringUtils.hasText(dto.getProvider())) {
                config.setProvider(dto.getProvider());
            }
            if (StringUtils.hasText(dto.getBaseUrl())) {
                config.setBaseUrl(dto.getBaseUrl());
            }
            if (StringUtils.hasText(dto.getModel())) {
                config.setModel(dto.getModel());
            }
            apiKey = StringUtils.hasText(dto.getApiKey()) ? dto.getApiKey().trim()
                    : aiModelFactory.decryptApiKey(config);
        } else {
            if (!StringUtils.hasText(dto.getApiKey())) {
                throw new BusinessException(ResultCode.AI_MODEL_TEST_FAILED, "请输入 API Key");
            }
            config = new AiModelConfig();
            config.setId(-1L);
            config.setConfigName("临时测试");
            config.setProvider(dto.getProvider());
            config.setBaseUrl(dto.getBaseUrl());
            config.setModel(dto.getModel());
            config.setTemperature(DEFAULT_TEMPERATURE);
            config.setMaxTokens(DEFAULT_MAX_TOKENS);
            config.setTimeoutMs(DEFAULT_TIMEOUT_MS);
            apiKey = dto.getApiKey().trim();
        }

        long start = System.currentTimeMillis();
        String reply;
        try {
            reply = aiModelFactory.probe(config, apiKey);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[模型配置] 连通性测试失败 config={} model={}", config.getConfigName(), config.getModel(), e);
            throw new BusinessException(ResultCode.AI_MODEL_TEST_FAILED,
                    "调用失败:" + briefMessage(e));
        }
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("success", true);
        result.put("costMs", System.currentTimeMillis() - start);
        result.put("reply", reply);
        return result;
    }

    /**
     * 查询并校验配置存在
     */
    public AiModelConfig requireConfig(Long id) {
        AiModelConfig entity = aiModelConfigMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.AI_MODEL_NOT_EXIST);
        }
        return entity;
    }

    /**
     * 填充默认值与空串处理
     */
    private void applyDefaults(AiModelConfig entity) {
        if (entity.getTemperature() == null) {
            entity.setTemperature(DEFAULT_TEMPERATURE);
        }
        if (entity.getMaxTokens() == null) {
            entity.setMaxTokens(DEFAULT_MAX_TOKENS);
        }
        if (entity.getTimeoutMs() == null) {
            entity.setTimeoutMs(DEFAULT_TIMEOUT_MS);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        if (entity.getIsDefault() == null) {
            entity.setIsDefault(0);
        }
        if (entity.getBaseUrl() != null && entity.getBaseUrl().isBlank()) {
            entity.setBaseUrl(null);
        }
    }

    /**
     * 清除其它配置的默认标记
     */
    private void clearOtherDefault(Long keepId) {
        AiModelConfig reset = new AiModelConfig();
        reset.setIsDefault(0);
        aiModelConfigMapper.update(reset, Wrappers.<AiModelConfig>lambdaUpdate()
                .eq(AiModelConfig::getIsDefault, 1)
                .ne(AiModelConfig::getId, keepId));
    }

    /**
     * 判断是否已是最后一个启用配置
     */
    private boolean isLastEnabled(Long id) {
        Long count = aiModelConfigMapper.selectCount(Wrappers.<AiModelConfig>lambdaQuery()
                .eq(AiModelConfig::getStatus, 1));
        return count != null && count == 1 && Integer.valueOf(1).equals(
                aiModelConfigMapper.selectById(id).getStatus());
    }

    /**
     * 确保至少有一个启用配置被标记为默认
     */
    private void ensureDefaultExists() {
        Long defaultCount = aiModelConfigMapper.selectCount(Wrappers.<AiModelConfig>lambdaQuery()
                .eq(AiModelConfig::getIsDefault, 1)
                .eq(AiModelConfig::getStatus, 1));
        if (defaultCount != null && defaultCount > 0) {
            return;
        }
        List<AiModelConfig> enabled = aiModelConfigMapper.selectList(Wrappers.<AiModelConfig>lambdaQuery()
                .eq(AiModelConfig::getStatus, 1)
                .orderByDesc(AiModelConfig::getId)
                .last("LIMIT 1"));
        if (!enabled.isEmpty()) {
            AiModelConfig target = enabled.get(0);
            target.setIsDefault(1);
            aiModelConfigMapper.updateById(target);
            log.info("[模型配置] 已自动将 id={} 设为默认配置", target.getId());
        }
    }

    /**
     * 提取异常关键信息,避免把整段堆栈返回给前端
     */
    private String briefMessage(Exception e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
            return e.getClass().getSimpleName();
        }
        return message.length() > 200 ? message.substring(0, 200) + "..." : message;
    }
}
