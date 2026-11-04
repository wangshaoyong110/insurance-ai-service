package com.insure.ai.modules.system.controller;

import com.insure.ai.aspect.Log;
import com.insure.ai.common.PageResult;
import com.insure.ai.common.Result;
import com.insure.ai.common.util.SecurityUtils;
import com.insure.ai.modules.system.dto.UserQuery;
import com.insure.ai.modules.system.dto.UserSaveDTO;
import com.insure.ai.modules.system.dto.UserVO;
import com.insure.ai.modules.system.entity.SysRole;
import com.insure.ai.modules.system.service.SysUserService;
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
 * 后台管理员账号管理控制器(仅管理端角色可访问)
 *
 * @author insurance-ai
 */
@Tag(name = "管理端-管理员账号", description = "管理端账号维护、角色分配、启停与重置密码")
@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final SysUserService sysUserService;

    /**
     * 分页查询管理员列表
     */
    @GetMapping("/page")
    public Result<PageResult<UserVO>> page(UserQuery query) {
        return Result.success(sysUserService.page(query));
    }

    /**
     * 角色下拉选项
     */
    @GetMapping("/roles")
    public Result<List<SysRole>> roles() {
        return Result.success(sysUserService.listRoles());
    }

    /**
     * 管理员详情
     */
    @GetMapping("/{id}")
    public Result<UserVO> detail(@PathVariable Long id) {
        return Result.success(sysUserService.detail(id));
    }

    /**
     * 新增管理员
     */
    @Log(module = "系统管理-账号", operation = "新增管理员")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody UserSaveDTO dto) {
        dto.setId(null);
        return Result.success("新增成功", sysUserService.save(dto));
    }

    /**
     * 编辑管理员
     */
    @Log(module = "系统管理-账号", operation = "编辑管理员")
    @PutMapping("/{id}")
    public Result<Long> update(@PathVariable Long id, @Valid @RequestBody UserSaveDTO dto) {
        dto.setId(id);
        return Result.success("修改成功", sysUserService.save(dto));
    }

    /**
     * 删除管理员
     */
    @Log(module = "系统管理-账号", operation = "删除管理员")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.delete(id, SecurityUtils.getUserId());
        return Result.success("删除成功", null);
    }

    /**
     * 重置密码
     */
    @Log(module = "系统管理-账号", operation = "重置管理员密码")
    @PutMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        sysUserService.resetPassword(id);
        return Result.success("密码已重置为 123456", null);
    }

    /**
     * 启用 / 禁用管理员
     */
    @Log(module = "系统管理-账号", operation = "修改管理员状态")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        sysUserService.changeStatus(id, status);
        return Result.success();
    }
}
