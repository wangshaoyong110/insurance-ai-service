package com.insure.ai.modules.kb.controller;

import com.insure.ai.aspect.Log;
import com.insure.ai.common.PageResult;
import com.insure.ai.common.Result;
import com.insure.ai.common.enums.InsuranceCategoryEnum;
import com.insure.ai.modules.kb.dto.KbQuery;
import com.insure.ai.modules.kb.dto.KbSaveDTO;
import com.insure.ai.modules.kb.entity.KbQuestion;
import com.insure.ai.modules.kb.service.KbQuestionService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 后台知识库管理控制器
 *
 * @author insurance-ai
 */
@Tag(name = "管理端-知识库", description = "保险问答语料的增删改查与批量导入")
@RestController
@RequestMapping("/admin/kb")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminKbController {

    private final KbQuestionService kbQuestionService;

    /**
     * 知识库分页
     */
    @GetMapping("/page")
    public Result<PageResult<KbQuestion>> page(KbQuery query) {
        return Result.success(kbQuestionService.page(query));
    }

    /**
     * 新增知识库
     */
    @Log(module = "知识库管理", operation = "新增知识")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody KbSaveDTO dto) {
        dto.setId(null);
        return Result.success("新增成功", kbQuestionService.save(dto));
    }

    /**
     * 编辑知识库
     */
    @Log(module = "知识库管理", operation = "编辑知识")
    @PutMapping("/{id}")
    public Result<Long> update(@PathVariable Long id, @Valid @RequestBody KbSaveDTO dto) {
        dto.setId(id);
        return Result.success("修改成功", kbQuestionService.save(dto));
    }

    /**
     * 删除知识库
     */
    @Log(module = "知识库管理", operation = "删除知识")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        kbQuestionService.delete(id);
        return Result.success("删除成功", null);
    }

    /**
     * 业务分类字典
     */
    @GetMapping("/categories")
    public Result<List<Map<String, String>>> categories() {
        return Result.success(Arrays.stream(InsuranceCategoryEnum.values())
                .map(item -> Map.of("code", item.getCode(), "name", item.getDesc()))
                .toList());
    }
}
