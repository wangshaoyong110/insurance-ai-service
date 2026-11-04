package com.insure.ai.modules.ai.controller;

import com.insure.ai.aspect.Log;
import com.insure.ai.common.Result;
import com.insure.ai.modules.ai.dto.AiModelSaveDTO;
import com.insure.ai.modules.ai.dto.AiModelTestDTO;
import com.insure.ai.modules.ai.entity.AiModelConfig;
import com.insure.ai.modules.ai.service.AiModelConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 后台大模型配置控制器
 * <p>API Key 加密存储,列表接口只返回掩码</p>
 *
 * @author insurance-ai
 */
@Tag(name = "管理端-模型配置", description = "大模型接入配置:密钥加密存储、连通性测试、默认模型切换")
@RestController
@RequestMapping("/admin/model")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiModelController {

    private final AiModelConfigService aiModelConfigService;

    /**
     * 配置列表
     */
    @GetMapping("/list")
    public Result<List<AiModelConfig>> list() {
        return Result.success(aiModelConfigService.list());
    }

    /**
     * 新增配置
     */
    @Log(module = "系统管理-模型配置", operation = "新增模型配置")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody AiModelSaveDTO dto) {
        dto.setId(null);
        return Result.success("新增成功", aiModelConfigService.save(dto));
    }

    /**
     * 编辑配置
     */
    @Log(module = "系统管理-模型配置", operation = "编辑模型配置")
    @PutMapping("/{id}")
    public Result<Long> update(@PathVariable Long id, @Valid @RequestBody AiModelSaveDTO dto) {
        dto.setId(id);
        return Result.success("修改成功", aiModelConfigService.save(dto));
    }

    /**
     * 删除配置
     */
    @Log(module = "系统管理-模型配置", operation = "删除模型配置")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        aiModelConfigService.delete(id);
        return Result.success("删除成功", null);
    }

    /**
     * 启用 / 停用
     */
    @Log(module = "系统管理-模型配置", operation = "变更模型配置状态")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        aiModelConfigService.changeStatus(id, status);
        return Result.success("操作成功", null);
    }

    /**
     * 设为默认配置
     */
    @Log(module = "系统管理-模型配置", operation = "设为默认模型")
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        aiModelConfigService.setDefault(id);
        return Result.success("已设为默认配置", null);
    }

    /**
     * 连通性测试
     */
    @Log(module = "系统管理-模型配置", operation = "测试模型连通性")
    @PostMapping("/test")
    public Result<Map<String, Object>> test(@RequestBody AiModelTestDTO dto) {
        return Result.success(aiModelConfigService.test(dto));
    }
}
