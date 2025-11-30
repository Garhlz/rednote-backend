package com.szu.afternoon3.platform.controller;

import com.szu.afternoon3.platform.dto.AccountLoginDTO;
import com.szu.afternoon3.platform.dto.SendEmailCodeDTO;
import com.szu.afternoon3.platform.dto.WechatLoginDTO;
import com.szu.afternoon3.platform.vo.LoginVO;
import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.service.AuthService;
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
}