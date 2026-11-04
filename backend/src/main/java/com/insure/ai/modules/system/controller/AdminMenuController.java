package com.insure.ai.modules.system.controller;

import com.insure.ai.aspect.Log;
import com.insure.ai.common.Result;
import com.insure.ai.modules.system.dto.MenuSaveDTO;
import com.insure.ai.modules.system.entity.SysMenu;
import com.insure.ai.modules.system.service.MenuService;
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

/**
 * 后台菜单管理控制器
 *
 * @author insurance-ai
 */
@Tag(name = "管理端-菜单管理", description = "菜单树维护,决定前端动态路由与侧边栏")
@RestController
@RequestMapping("/admin/menu")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMenuController {

    private final MenuService menuService;

    /**
     * 菜单树(包含停用菜单,供管理页维护)
     */
    @GetMapping("/tree")
    public Result<List<SysMenu>> tree(@RequestParam(defaultValue = "false") boolean onlyEnabled) {
        return Result.success(menuService.tree(onlyEnabled));
    }

    /**
     * 新增菜单
     */
    @Log(module = "系统管理-菜单", operation = "新增菜单")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody MenuSaveDTO dto) {
        dto.setId(null);
        return Result.success("新增成功", menuService.save(dto));
    }

    /**
     * 编辑菜单
     */
    @Log(module = "系统管理-菜单", operation = "编辑菜单")
    @PutMapping("/{id}")
    public Result<Long> update(@PathVariable Long id, @Valid @RequestBody MenuSaveDTO dto) {
        dto.setId(id);
        return Result.success("修改成功", menuService.save(dto));
    }

    /**
     * 删除菜单
     */
    @Log(module = "系统管理-菜单", operation = "删除菜单")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return Result.success("删除成功", null);
    }
}
