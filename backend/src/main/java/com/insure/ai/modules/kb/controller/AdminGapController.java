package com.insure.ai.modules.kb.controller;

import com.insure.ai.aspect.Log;
import com.insure.ai.common.PageResult;
import com.insure.ai.common.Result;
import com.insure.ai.modules.kb.dto.GapGroupVO;
import com.insure.ai.modules.kb.dto.GapToKbDTO;
import com.insure.ai.modules.kb.service.GapAnalysisService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端-知识缺口
 * <p>把"客户问了但知识库没答上来"的问题暴露给运营，并支持一键转成标准问答</p>
 *
 * @author insurance-ai
 */
@Tag(name = "管理端-知识缺口", description = "未命中问题聚合、转知识库、忽略")
@RestController
@RequestMapping("/admin/kb/gap")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminGapController {

    private final GapAnalysisService gapAnalysisService;

    /**
     * 缺口分页(按问题聚合,次数降序)
     */
    @GetMapping("/page")
    public Result<PageResult<GapGroupVO>> page(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Integer status,
                                               @RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(gapAnalysisService.page(keyword, status, pageNum, pageSize));
    }

    /**
     * 转成知识库条目
     */
    @Log(module = "知识缺口", operation = "转为知识")
    @PostMapping("/{id}/to-kb")
    public Result<Long> toKnowledge(@PathVariable Long id, @Valid @RequestBody GapToKbDTO dto) {
        return Result.success("已生成知识条目",
                gapAnalysisService.toKnowledge(id, dto.getCategory(), dto.getAnswer(), dto.getKeywords()));
    }

    /**
     * 忽略(业务上不需要覆盖的问题，如闲聊)
     */
    @Log(module = "知识缺口", operation = "忽略缺口")
    @PutMapping("/{id}/ignore")
    public Result<Void> ignore(@PathVariable Long id) {
        gapAnalysisService.ignore(id);
        return Result.success();
    }

    /**
     * 缺口统计
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(gapAnalysisService.statistics());
    }
}
