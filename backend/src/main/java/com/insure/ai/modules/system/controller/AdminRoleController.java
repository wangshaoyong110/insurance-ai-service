package com.insure.ai.modules.system.controller;

import com.insure.ai.aspect.Log;
import com.insure.ai.common.Result;
import com.insure.ai.modules.system.dto.RoleSaveDTO;
import com.insure.ai.modules.system.entity.SysRole;
import com.insure.ai.modules.system.service.RoleService;
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

import java.util.List;
import java.util.Map;

/**
 * 后台角色管理控制器
 *
 * @author insurance-ai
 */
@Tag(name = "管理端-角色管理", description = "角色维护与菜单授权")
@RestController
@RequestMapping("/admin/role")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRoleController {

    private final RoleService roleService;

    /**
     * 角色列表
     */
    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        return Result.success(roleService.list());
    }

    /**
     * 角色下拉选项
     */
    @GetMapping("/options")
    public Result<List<SysRole>> options() {
        return Result.success(roleService.options());
    }

    /**
     * 角色详情(含已授权菜单ID)
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(roleService.detail(id));
    }

    /**
     * 新增角色
     */
    @Log(module = "系统管理-角色", operation = "新增角色")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody RoleSaveDTO dto) {
        dto.setId(null);
        return Result.success("新增成功", roleService.save(dto));
    }

    /**
     * 编辑角色(含菜单授权)
     */
    @Log(module = "系统管理-角色", operation = "编辑角色")
    @PutMapping("/{id}")
    public Result<Long> update(@PathVariable Long id, @Valid @RequestBody RoleSaveDTO dto) {
        dto.setId(id);
        return Result.success("修改成功", roleService.save(dto));
    }

    /**
     * 删除角色
     */
    @Log(module = "系统管理-角色", operation = "删除角色")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.success("删除成功", null);
    }
}
