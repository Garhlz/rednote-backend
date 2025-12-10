package com.szu.afternoon3.platform.controller;

import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.service.AdminService;
import com.szu.afternoon3.platform.service.AuthService;
import com.szu.afternoon3.platform.vo.LoginVO;
import com.szu.afternoon3.platform.vo.PostVO;
import com.szu.afternoon3.platform.vo.UserInfo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;
    
    @Autowired
    private AuthService authService; // 复用 resetPassword 逻辑

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid AccountLoginDTO loginDTO) {
        LoginVO loginVO = adminService.login(loginDTO.getAccount(), loginDTO.getPassword());
        return Result.success(loginVO);
    }

    @GetMapping("/me")
    public Result<UserInfo> getAdminInfo() {
        return Result.success(adminService.getAdminInfo());
    }

    // 管理员修改密码 (通过邮箱验证码)
    // 复用 UserPasswordResetDTO (email, code, newPassword)
    // 注意：虽然是“修改密码”，但业务流程是“重置密码”流程
    @PostMapping("/password/change")
    public Result<Void> changePassword(@RequestBody @Valid UserPasswordResetDTO dto) {
        // 这里直接复用 AuthService 的 resetPassword，因为它就是 email+code+newPwd
        // 也可以在 AdminService 里包一层，确保是改自己的？
        // 既然输入了 email 和 code，且验证通过，那就是安全的。
        authService.resetPassword(dto);
        return Result.success();
    }

    // 用户管理 - 列表
    @PostMapping("/users/list")
    public Result<Map<String, Object>> getUserList(@RequestBody AdminUserSearchDTO dto) {
        return Result.success(adminService.getUserList(dto));
    }

    // 用户管理 - 删除
    @PostMapping("/users/{id}/delete")
    public Result<Void> deleteUser(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reason = body.get("reason");
        adminService.deleteUser(id, reason);
        return Result.success();
    }

    // 内容审核 - 列表
    @PostMapping("/posts/list")
    public Result<Map<String, Object>> getPostList(@RequestBody AdminPostSearchDTO dto) {
        return Result.success(adminService.getPostList(dto));
    }

    // 内容审核 - 详情
    @GetMapping("/posts/{id}")
    public Result<PostVO> getPostDetail(@PathVariable String id) {
        return Result.success(adminService.getPostDetail(id));
    }

    // 内容审核 - 审核
    @PostMapping("/posts/{id}/audit")
    public Result<Void> auditPost(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Integer status = (Integer) body.get("status");
        String reason = (String) body.get("reason");
        adminService.auditPost(id, status, reason);
        return Result.success();
    }
}
