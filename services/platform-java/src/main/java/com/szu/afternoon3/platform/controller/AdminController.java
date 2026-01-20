package com.szu.afternoon3.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.szu.afternoon3.platform.annotation.OperationLog;
import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.enums.ResultCode;
import com.szu.afternoon3.platform.exception.AppException;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.repository.PostRepository;
import com.szu.afternoon3.platform.service.*;
import com.szu.afternoon3.platform.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 后台管理控制器
 * 处理管理员登录、用户管理、内容审核等逻辑
 */
@Slf4j
@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private AiService aiService;

    @Autowired
    private PostService postService;
    /**
     * 管理员登录
     * @param loginDTO 登录参数
     * @return 登录凭证信息
     */
    @PostMapping("/auth/login")
    @OperationLog(module = "后台认证", description = "管理员登录", bizId = "#loginDTO.account")
    public Result<LoginVO> login(@RequestBody @Valid AccountLoginDTO loginDTO) {
        LoginVO loginVO = adminService.login(loginDTO.getAccount(), loginDTO.getPassword());
        return Result.success(loginVO);
    }

    /**
     * 发送管理员验证码 (用于重置密码)
     * @param dto 邮箱参数
     */
    @PostMapping("/auth/send-code")
    @OperationLog(module = "后台认证", description = "发送验证码", bizId = "#dto.email")
    public Result<Void> sendCode(@RequestBody @Valid SendEmailCodeDTO dto) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, dto.getEmail()));

        if (user == null) {
            throw new AppException(ResultCode.USER_NOT_FOUND);
        }
        if(!user.getRole().equals("ADMIN")){
            throw new AppException(ResultCode.PERMISSION_DENIED);
        }
        authService.sendEmailCode(dto.getEmail());
        return Result.success();
    }

    /**
     * 管理员重置密码 (未登录状态)
     * @param dto 重置参数
     */
    @PostMapping("/auth/reset-password")
    @OperationLog(module = "后台认证", description = "忘记密码重置", bizId = "#dto.email")
    public Result<Void> changePassword(@RequestBody @Valid UserPasswordResetDTO dto) {
        authService.resetPassword(dto);
        return Result.success();
    }

    /**
     * 退出登录
     * @param request HTTP请求
     */
    @PostMapping("/auth/logout")
    @OperationLog(module = "后台认证", description = "退出登录")
    public Result<Void> logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            authService.logout(token.substring(7));
        }
        return Result.success();
    }

    /**
     * 创建测试用户 (开发辅助)
     * @param dto 用户信息
     * @return 用户ID
     */
    @PostMapping("/auth/test/register")
    @OperationLog(module = "后台辅助", description = "创建测试用户", bizId = "#dto.email")
    public Result<Long> createTestUser(@RequestBody @Valid TestUserCreateDTO dto) {
        Long userId = adminService.createTestUser(dto);
        return Result.success(userId);
    }

    @PostMapping("/auth/refresh")
    public Result<LoginVO> refreshToken(@RequestBody RefreshTokenDTO dto) {
        return Result.success(authService.refreshToken(dto.getRefreshToken()));
    }
    /**
     * 获取管理员个人信息
     * @return 管理员信息
     */
    @GetMapping("/profile/info")
    @OperationLog(module = "后台个人中心", description = "获取个人信息")
    public Result<UserInfo> getAdminInfo() {
        return Result.success(adminService.getAdminInfo());
    }

    /**
     * 登录后修改/重置密码
     * @param dto 密码参数
     */
    @PostMapping("/profile/password")
    @OperationLog(module = "后台个人中心", description = "修改密码", bizId = "#dto.email")
    public Result<Void> updatePassword(@RequestBody @Valid UserPasswordResetDTO dto) {
        authService.resetPassword(dto);
        return Result.success();
    }

    /**
     * 用户管理列表查询
     * @param dto 查询条件
     * @return 用户分页列表
     */
    @PostMapping("/user/list")
    @OperationLog(module = "后台用户管理", description = "查询用户列表")
    public Result<PageResult<AdminUserVO>> getUserList(@RequestBody AdminUserSearchDTO dto) {
        return Result.success(adminService.getUserList(dto));
    }

    /**
     * 获取用户详细信息 (聚合视图)
     * @param id 用户ID
     * @return 用户详情
     */
    @GetMapping("/user/{id}")
    @OperationLog(module = "后台用户管理", description = "查看用户详情", bizId = "#id")
    public Result<AdminUserDetailVO> getUserDetail(@PathVariable Long id) {
        return Result.success(adminService.getUserDetail(id));
    }

    /**
     * 删除/封禁用户
     * @param id 用户ID
     * @param dto 删除原因
     */
    @PostMapping("/user/{id}")
    @OperationLog(module = "后台用户管理", description = "删除用户", bizId = "#id")
    public Result<Void> deleteUser(@PathVariable Long id, @RequestBody @Valid AdminUserDeleteDTO dto) {
        adminService.deleteUser(id, dto.getReason());
        return Result.success();
    }

    /**
     * 待审核帖子列表查询
     * @param dto 查询条件
     * @return 帖子分页列表
     */
    @PostMapping("/post/audit-list")
    @OperationLog(module = "后台内容审核", description = "查询审核列表")
    public Result<PageResult<AdminPostVO>> getPostList(@RequestBody AdminPostSearchDTO dto) {
        return Result.success(adminService.getPostList(dto));
    }

    /**
     * 获取帖子详情 (审核用)
     * @param id 帖子ID
     * @return 帖子详情
     */
    @GetMapping("/post/{id}")
    @OperationLog(module = "后台内容审核", description = "查看帖子详情", bizId = "#id")
    public Result<PostVO> getPostDetail(@PathVariable String id) {
        return Result.success(adminService.getPostDetail(id));
    }

    /**
     * 审核帖子操作
     * @param id 帖子ID
     * @param dto 审核结果
     */
    @PostMapping("/post/{id}/audit")
    @OperationLog(module = "后台内容审核", description = "审核帖子", bizId = "#id")
    public Result<Void> auditPost(@PathVariable String id, @RequestBody @Valid AdminPostAuditDTO dto) {
        adminService.auditPost(id, dto.getStatus(), dto.getReason());
        return Result.success();
    }

    @GetMapping("/post/{postId}/audit-history")
    @OperationLog(module = "后台内容审核", description = "获取帖子审核历史", bizId = "#postId")
    public Result<List<PostAuditLogVO>> getPostAuditHistory(@PathVariable String postId) {
        return Result.success(adminService.getPostAuditHistory(postId));
    }

    @DeleteMapping("/post/{postId}")
    @OperationLog(module = "后台内容审核", description = "管理员强制删除帖子", bizId = "#postId") // 补全 bizId
    public Result<Void> deletePost(
            @PathVariable String postId,
            @RequestParam(required = false) String reason
    ) {
        // 直接复用 Service，reason 会传给 Listener
        postService.deletePost(postId, reason);
        return Result.success();
    }

    /**
     * 查询管理员操作日志
     * @param dto 查询条件
     * @return 日志列表
     */
    @PostMapping("/log/admin")
    @OperationLog(module = "后台日志审计", description = "查询管理员日志")
    public Result<PageResult<ApiLogVO>> getAdminLogs(@RequestBody LogSearchDTO dto) {
        return Result.success(adminService.getAdminLogs(dto));
    }

    /**
     * 查询C端用户操作日志
     * @param dto 查询条件
     * @return 日志列表
     */
    @PostMapping("/log/user")
    @OperationLog(module = "后台日志审计", description = "查询用户日志")
    public Result<PageResult<ApiLogVO>> getUserLogs(@RequestBody LogSearchDTO dto) {
        return Result.success(adminService.getUserLogs(dto));
    }

    /**
     * 导出操作日志
     * @param dto 查询条件
     * @param response HTTP响应对象
     */
    @GetMapping("/log/export")
    @OperationLog(module = "后台日志审计", description = "导出操作日志")
    public void exportLogs(LogSearchDTO dto, HttpServletResponse response) {
        adminService.exportLogs(dto, response);
    }

    /**
     * 管理员手动触发 AI 审核
     * @param postId 帖子ID
     */
    @PostMapping("/post/{postId}/audit/ai")
    @OperationLog(module = "后台内容审核", description = "ai审核帖子", bizId = "#postId")
    public Result<AiAuditResultVO> manualAuditPost(@PathVariable String postId) {
        // 1. 查库
        PostDoc post = postRepository.findById(postId).orElse(null);
        if (post == null) {
            throw new AppException(ResultCode.RESOURCE_NOT_FOUND);
        }

        // 2. 只有“非删除”状态的帖子才有审核意义（可选）
        if (post.getIsDeleted() != null && post.getIsDeleted() == 1) {
            throw new AppException(ResultCode.RESOURCE_NOT_FOUND, "帖子已被删除");
        }

        // 3. 调用 AI 审核
        // 注意：这是一个同步调用，可能会耗时 3-5 秒，前端建议加 Loading
        AiAuditResultVO result = aiService.auditPostContent(post);

        result.setPostId(postId);

        log.info("AI 审核结果 - ID: {}, 结论: {}, 原因: {}", postId, result.getConclusion(), result.getSuggestion());

        // 4. 返回给前端展示，暂不自动修改数据库状态，由管理员决定
        return Result.success(result);
    }

    /**
     * 获取帖子统计的浏览量排行
     * @param limit 帖子数量
     */
    @GetMapping("/stats/top-views")
    @OperationLog(module = "数据统计", description = "查看浏览量排行")
    public Result<List<AdminPostStatVO>> getTopViewPosts(@RequestParam(defaultValue = "20") int limit) {
        if (limit > 100) limit = 100; // 限制最大查询数，防止查崩
        List<AdminPostStatVO> list = adminService.getPostViewRanking(limit);
        return Result.success(list);
    }


    @GetMapping("/stats")
    @OperationLog(module = "数据统计", description = "获取后台首页统计数据")
    public Result<AdminStatsVO> getStats() {
        return Result.success(adminService.getDataStatistics());
    }


}