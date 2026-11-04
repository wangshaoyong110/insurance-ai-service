package com.insure.ai.modules.system.controller;

import com.insure.ai.aspect.Log;
import com.insure.ai.common.PageResult;
import com.insure.ai.common.Result;
import com.insure.ai.modules.system.dto.CusUserQuery;
import com.insure.ai.modules.system.dto.CusUserVO;
import com.insure.ai.modules.system.service.CusUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台客户管理控制器(管理 cus_user,与管理员账号隔离)
 *
 * @author insurance-ai
 */
@Tag(name = "管理端-客户管理", description = "客户账号查询、启停、重置密码")
@RestController
@RequestMapping("/admin/customer")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCustomerController {

    private final CusUserService cusUserService;

    /**
     * 客户分页列表
     */
    @GetMapping("/page")
    public Result<PageResult<CusUserVO>> page(CusUserQuery query) {
        return Result.success(cusUserService.page(query));
    }

    /**
     * 客户详情
     */
    @GetMapping("/{id}")
    public Result<CusUserVO> detail(@PathVariable Long id) {
        return Result.success(cusUserService.detail(id));
    }

    /**
     * 启用 / 禁用客户账号
     */
    @Log(module = "客户服务-客户", operation = "修改客户状态")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        cusUserService.changeStatus(id, status);
        return Result.success();
    }

    /**
     * 重置客户密码
     */
    @Log(module = "客户服务-客户", operation = "重置客户密码")
    @PutMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        cusUserService.resetPassword(id);
        return Result.success("客户密码已重置为 123456", null);
    }

    /**
     * 删除客户(逻辑删除)
     */
    @Log(module = "客户服务-客户", operation = "删除客户")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        cusUserService.delete(id);
        return Result.success("删除成功", null);
    }
}
