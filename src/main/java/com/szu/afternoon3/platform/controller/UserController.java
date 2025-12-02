package com.szu.afternoon3.platform.controller;

import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.service.UserService;
import com.szu.afternoon3.platform.vo.UserProfileVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 获取个人资料
     * 对应 Apifox 接口: /api/user/profile (GET)
     */
    @GetMapping("/profile")
    public Result<UserProfileVO> getProfile() {
        UserProfileVO vo = userService.getUserProfile();
        return Result.success(vo);
    }

    /**
     * 修改个人资料
     * 对应 Apifox 接口: /api/user/profile (PUT)
     */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody @Valid UserProfileUpdateDTO dto) {
        userService.updateProfile(dto);
        return Result.success(null);
    }

    /**
     * 绑定邮箱
     * 对应 Apifox 接口: /api/user/bind-email (POST)
     */
    @PostMapping("/bind-email")
    public Result<Map<String, String>> bindEmail(@RequestBody @Valid UserBindEmailDTO dto) {
        Map<String, String> result = userService.bindEmail(dto);
        return Result.success(result);
    }

// 删除了这个接口。这个接口是冗余的（在apifox中也删除了）
//    /**
//     * 初始化/修改密码 (简单模式，无需验证码)
//     * 对应 Apifox 接口: /api/user/set-password (POST)
//     */
//    @PostMapping("/set-password")
//    public Result<Void> setPassword(@RequestBody @Valid UserSetPasswordSimpleDTO dto) {
//        userService.setPassword(dto);
//        return Result.success(null);
//    }

    /**
     * 首次设置密码 (验证邮箱模式)
     * 对应 Apifox 接口: /api/user/password/set (POST)
     */
    @PostMapping("/password/set")
    public Result<Void> setPasswordWithCode(@RequestBody @Valid UserPasswordSetDTO dto) {
        userService.setPasswordWithCode(dto);
        return Result.success(null);
    }

    /**
     * 修改密码 (验证旧密码)
     * 对应 Apifox 接口: /api/user/password/change (POST)
     */
    @PostMapping("/password/change")
    public Result<Void> changePassword(@RequestBody @Valid UserPasswordChangeDTO dto) {
        userService.changePassword(dto);
        return Result.success(null);
    }

    /**
     * 获取关注列表
     * 对应 Apifox 接口: /api/user/follows/{userId} (GET)
     */
    @GetMapping("/follows/{userId}")
    public Result<Map<String, Object>> getFollows(
            @PathVariable String userId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size
    ) {
        Map<String, Object> data = userService.getFollowList(userId, page, size);
        return Result.success(data);
    }
}
