package com.szu.afternoon3.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.enums.ResultCode;
import com.szu.afternoon3.platform.exception.AppException;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.service.AdminService;
import com.szu.afternoon3.platform.service.AuthService;
import com.szu.afternoon3.platform.service.UserService;
import com.szu.afternoon3.platform.vo.AdminUserDetailVO;
import com.szu.afternoon3.platform.vo.LoginVO;
import com.szu.afternoon3.platform.vo.PostVO;
import com.szu.afternoon3.platform.vo.UserInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private AuthService authService; // 复用 resetPassword 逻辑

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @PostMapping("/auth/login")
    public Result<LoginVO> login(@RequestBody @Valid AccountLoginDTO loginDTO) {
        LoginVO loginVO = adminService.login(loginDTO.getAccount(), loginDTO.getPassword());
        return Result.success(loginVO);
    }

    @PostMapping("/auth/send-code")
    public Result<Void> sendCode(@RequestBody @Valid SendEmailCodeDTO dto) {
        // 根据邮箱查找用户, 验证该邮箱是否注册
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, dto.getEmail()));

        if (user == null) {
            throw new AppException(ResultCode.USER_NOT_FOUND);
        }
        // 当前是管理端，只有管理员才可以发送邮件
        if(!user.getRole().equals("ADMIN")){
            throw new AppException(ResultCode.PERMISSION_DENIED);
        }
        // 复用 AuthService 的发送逻辑
        authService.sendEmailCode(dto.getEmail());
        return Result.success();
    }

    // 管理员修改密码 (通过邮箱验证码)
    @PostMapping("/auth/reset-password")
    public Result<Void> changePassword(@RequestBody @Valid UserPasswordResetDTO dto) {
        authService.resetPassword(dto);
        return Result.success();
    }

    // 退出登录 (补充文档中存在的接口)
    @PostMapping("/auth/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            authService.logout(token.substring(7));
        }
        return Result.success();
    }

    /**
     * 创建测试用户 (开发环境便利接口)
     */
    @PostMapping("/auth/test/register")
    public Result<Long> createTestUser(@RequestBody @Valid TestUserCreateDTO dto) {
        Long userId = adminService.createTestUser(dto);
        return Result.success(userId);
    }

    // 个人信息
    @GetMapping("/profile/info")
    public Result<UserInfo> getAdminInfo() {
        return Result.success(adminService.getAdminInfo());
    }

    // 登录之后重置密码
    // 直接修改为相同的通过邮箱验证码验证的逻辑好了
    @PostMapping("/profile/password")
    public Result<Void> updatePassword(@RequestBody @Valid UserPasswordResetDTO dto) {
        authService.resetPassword(dto);
        return Result.success();
    }



    // 用户管理 - 列表
    @PostMapping("/user/list")
    public Result<Map<String, Object>> getUserList(@RequestBody AdminUserSearchDTO dto) {
        return Result.success(adminService.getUserList(dto));
    }

    // 获取用户详情 (聚合视图)
    @GetMapping("/users/{id}")
    public Result<AdminUserDetailVO> getUserDetail(@PathVariable Long id) {
        return Result.success(adminService.getUserDetail(id));
    }

    // 用户管理 - 删除
    @PostMapping("/user/{id}")
    public Result<Void> deleteUser(@PathVariable Long id, @RequestBody @Valid AdminUserDeleteDTO dto) {
        // 使用 DTO.getReason()，类型安全且由 Spring 自动校验非空
        adminService.deleteUser(id, dto.getReason());
        return Result.success();
    }

    // 内容审核 - 列表
    @PostMapping("/post/audit-list")
    public Result<Map<String, Object>> getPostList(@RequestBody AdminPostSearchDTO dto) {
        return Result.success(adminService.getPostList(dto));
    }

    // 内容审核 - 详情
    @GetMapping("/post/{id}")
    public Result<PostVO> getPostDetail(@PathVariable String id) {
        return Result.success(adminService.getPostDetail(id));
    }

    // 内容审核 - 审核
    @PostMapping("/post/{id}/audit")
    public Result<Void> auditPost(@PathVariable String id, @RequestBody @Valid AdminPostAuditDTO dto) {
        // 使用 DTO 获取参数
        adminService.auditPost(id, dto.getStatus(), dto.getReason());
        return Result.success();
    }


}
