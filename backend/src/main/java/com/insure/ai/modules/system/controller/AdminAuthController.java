package com.insure.ai.modules.system.controller;

import com.insure.ai.aspect.Log;
import com.insure.ai.common.Result;
import com.insure.ai.common.util.SecurityUtils;
import com.insure.ai.modules.system.dto.ChangePasswordDTO;
import com.insure.ai.modules.system.dto.LoginDTO;
import com.insure.ai.modules.system.dto.LoginVO;
import com.insure.ai.modules.system.dto.UserVO;
import com.insure.ai.modules.system.entity.SysMenu;
import com.insure.ai.modules.system.service.AdminAuthService;
import com.insure.ai.modules.system.service.MenuService;
import com.insure.ai.security.JwtUtils;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端认证控制器(业务操作人员使用)
 * <p>登录入口与客户端 /auth/login 分离,登录后返回该角色可见的菜单树</p>
 *
 * @author insurance-ai
 */
@Tag(name = "管理端-登录认证", description = "业务操作人员登录/退出/当前用户/菜单/改密")
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final MenuService menuService;
    private final JwtUtils jwtUtils;

    /**
     * 管理端登录(账号或手机号 + 密码)
     */
    @Log(module = "管理端-认证", operation = "管理员登录")
    @SecurityRequirements
    @PostMapping("/login")
    public Result<LoginVO<UserVO>> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        return Result.success("登录成功", adminAuthService.login(dto, request));
    }

    /**
     * 管理端退出登录
     */
    @Log(module = "管理端-认证", operation = "管理员退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        adminAuthService.logout(jwtUtils.resolveToken(request));
        return Result.success();
    }

    /**
     * 当前管理员信息
     */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(adminAuthService.currentUser(SecurityUtils.getUserId()));
    }

    /**
     * 当前管理员的菜单树(菜单驱动路由与侧边导航)
     */
    @GetMapping("/menus")
    public Result<List<SysMenu>> menus() {
        String roleCode = adminAuthService.currentRoleCode(SecurityUtils.getUserId());
        return Result.success(menuService.treeByRoleCode(roleCode));
    }

    /**
     * 修改密码
     */
    @Log(module = "管理端-认证", operation = "管理员修改密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        adminAuthService.changePassword(SecurityUtils.getUserId(), dto);
        return Result.success("密码修改成功", null);
    }
}
