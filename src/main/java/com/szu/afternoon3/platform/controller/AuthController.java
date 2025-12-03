package com.szu.afternoon3.platform.controller;

import cn.hutool.core.util.StrUtil;
import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.vo.LoginVO;
import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;
    @Autowired
    private HttpServletRequest request;
    // 微信一键登录
    @PostMapping("/login/wechat")
    public Result<LoginVO> wechatLogin(@RequestBody @Valid WechatLoginDTO loginDTO) {
        // 直接调用 Service，异常由全局异常处理器捕获（或者暂时让它报错500）
        LoginVO loginVO = authService.wechatLogin(loginDTO.getCode());
        return Result.success(loginVO);
    }

    // 账号密码登录
    @PostMapping("/login/account")
    public Result<LoginVO> accountLogin(@RequestBody @Valid AccountLoginDTO loginDTO) {
        LoginVO loginVO = authService.accountLogin(loginDTO.getAccount(), loginDTO.getPassword());
        return Result.success(loginVO);
    }

    @PostMapping("/send-code")
    public Result<Void> sendCode(@RequestBody @Valid SendEmailCodeDTO dto) {
        authService.sendEmailCode(dto.getEmail());
        return Result.success();
    }

    @PostMapping("/password/reset")
    public Result<Void> resetPassword(@RequestBody @Valid UserPasswordResetDTO dto) {
        authService.resetPassword(dto);
        return Result.success();
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        // 1. 从 Header 获取 Token
        String authHeader = request.getHeader("Authorization");
        if (StrUtil.isNotBlank(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            authService.logout(token);
        }
        return Result.success();
    }

    /**
     * 创建测试用户 (开发环境便利接口)
     */
    @PostMapping("/test/register")
    public Result<Long> createTestUser(@RequestBody @Valid TestUserCreateDTO dto) {
        Long userId = authService.createTestUser(dto);
        return Result.success(userId);
    }
}