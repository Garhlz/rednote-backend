package com.szu.afternoon3.platform.controller;

import cn.hutool.core.util.StrUtil;
import com.szu.afternoon3.platform.annotation.OperationLog;
import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.vo.LoginVO;
import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * 处理客户端(App/小程序)的登录注册流程
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;
    @Autowired
    private HttpServletRequest request;

    /**
     * 微信一键登录
     * @param loginDTO 包含微信code
     * @return 登录凭证及用户信息
     */
    @PostMapping("/login/wechat")
    @OperationLog(module = "认证模块", description = "微信登录")
    public Result<LoginVO> wechatLogin(@RequestBody @Valid WechatLoginDTO loginDTO) {
        LoginVO loginVO = authService.wechatLogin(loginDTO.getCode());
        return Result.success(loginVO);
    }

    /**
     * 账号密码登录
     * @param loginDTO 账号密码
     * @return 登录凭证及用户信息
     */
    @PostMapping("/login/account")
    @OperationLog(module = "认证模块", description = "账号登录", bizId = "#loginDTO.account")
    public Result<LoginVO> accountLogin(@RequestBody @Valid AccountLoginDTO loginDTO) {
        LoginVO loginVO = authService.accountLogin(loginDTO.getAccount(), loginDTO.getPassword());
        return Result.success(loginVO);
    }

    /**
     * 发送邮箱验证码
     * @param dto 邮箱地址
     */
    @PostMapping("/send-code")
    @OperationLog(module = "认证模块", description = "发送验证码", bizId = "#dto.email")
    public Result<Void> sendCode(@RequestBody @Valid SendEmailCodeDTO dto) {
        authService.sendEmailCode(dto.getEmail());
        return Result.success();
    }

    /**
     * 重置密码 (未登录状态)
     * @param dto 重置参数
     */
    @PostMapping("/password/reset")
    @OperationLog(module = "认证模块", description = "重置密码", bizId = "#dto.email")
    public Result<Void> resetPassword(@RequestBody @Valid UserPasswordResetDTO dto) {
        authService.resetPassword(dto);
        return Result.success();
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    @OperationLog(module = "认证模块", description = "退出登录")
    public Result<Void> logout() {
        String authHeader = request.getHeader("Authorization");
        if (StrUtil.isNotBlank(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            authService.logout(token);
        }
        return Result.success();
    }

    /**
     * 创建测试用户 (开发辅助)
     */
    @PostMapping("/test/register")
    @OperationLog(module = "认证模块", description = "注册测试用户", bizId = "#dto.email")
    public Result<Long> createTestUser(@RequestBody @Valid TestUserCreateDTO dto) {
        Long userId = authService.createTestUser(dto);
        return Result.success(userId);
    }

    @GetMapping("test")
    public Result<String> printHello(){
        return Result.success("Hello, world!");
    }
}