package com.insure.ai.modules.system.controller;

import com.insure.ai.aspect.Log;
import com.insure.ai.common.annotation.RateLimit;
import com.insure.ai.common.Result;
import com.insure.ai.common.util.SecurityUtils;
import com.insure.ai.modules.system.dto.ChangePasswordDTO;
import com.insure.ai.modules.system.dto.CusUserVO;
import com.insure.ai.modules.system.dto.LoginDTO;
import com.insure.ai.modules.system.dto.LoginVO;
import com.insure.ai.modules.system.dto.RegisterDTO;
import com.insure.ai.modules.system.dto.SmsCodeVO;
import com.insure.ai.modules.system.dto.SmsSendDTO;
import com.insure.ai.modules.system.service.CusAuthService;
import com.insure.ai.modules.system.service.SmsService;
import com.insure.ai.common.enums.SmsSceneEnum;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户端认证控制器(投保咨询客户使用)
 * <p>与后台管理登录入口 /admin/auth/login 完全独立</p>
 *
 * @author insurance-ai
 */
@Tag(name = "客户端-登录注册", description = "客户手机号登录、注册、资料与密码修改")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final CusAuthService cusAuthService;
    private final SmsService smsService;
    private final JwtUtils jwtUtils;

    /**
     * 客户登录(手机号或账号 + 密码)
     */
    @Log(module = "客户端-认证", operation = "客户登录")
    @SecurityRequirements
    @PostMapping("/login")
    public Result<LoginVO<CusUserVO>> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        return Result.success("登录成功", cusAuthService.login(dto, request));
    }

    /**
     * 获取短信验证码
     * <p>注册前必须先获取;验证码 5 分钟有效,同一手机号 60 秒内不可重复获取</p>
     */
    @Log(module = "客户端-认证", operation = "获取短信验证码")
    @RateLimit(limit = 5, seconds = 60, message = "获取验证码过于频繁,请稍后再试")
    @SecurityRequirements
    @PostMapping("/sms-code")
    public Result<SmsCodeVO> sendSmsCode(@Valid @RequestBody SmsSendDTO dto) {
        return Result.success("验证码已发送", smsService.sendVerifyCode(dto.getPhone(), SmsSceneEnum.of(dto.getScene())));
    }

    /**
     * 客户注册
     */
    @Log(module = "客户端-认证", operation = "客户注册")
    @SecurityRequirements
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success("注册成功", cusAuthService.register(dto));
    }

    /**
     * 客户退出登录
     */
    @Log(module = "客户端-认证", operation = "客户退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        cusAuthService.logout(jwtUtils.resolveToken(request));
        return Result.success();
    }

    /**
     * 当前登录客户信息
     */
    @GetMapping("/me")
    public Result<CusUserVO> me() {
        return Result.success(cusAuthService.currentUser(SecurityUtils.getUserId()));
    }

    /**
     * 修改密码
     */
    @Log(module = "客户端-认证", operation = "客户修改密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        cusAuthService.changePassword(SecurityUtils.getUserId(), dto);
        return Result.success("密码修改成功", null);
    }

    /**
     * 修改个人资料(昵称 / 邮箱)
     */
    @Log(module = "客户端-认证", operation = "修改个人资料")
    @PutMapping("/profile")
    public Result<CusUserVO> updateProfile(@RequestParam(required = false) String nickname,
                                           @RequestParam(required = false) String email) {
        return Result.success("资料已更新", cusAuthService.updateProfile(SecurityUtils.getUserId(), nickname, email));
    }
}
