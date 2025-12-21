package com.szu.afternoon3.platform.service;

import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.vo.*;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;

public interface AdminService {
    // 管理员登录
    LoginVO login(String account, String password);

    // 管理员获取自己的信息
    UserInfo getAdminInfo();

    // 用户管理 - 列表查询
    Map<String, Object> getUserList(AdminUserSearchDTO dto);

    // 用户管理 - 删除用户
    void deleteUser(Long userId, String reason);

    // 聚合用户详情
    public AdminUserDetailVO getUserDetail(Long userId);

    // 内容审核 - 列表查询
    Map<String, Object> getPostList(AdminPostSearchDTO dto);

    // 内容审核 - 获取详情
    PostVO getPostDetail(String postId);

    // 内容审核 - 审核操作
    void auditPost(String postId, Integer status, String reason);

    Long createTestUser(TestUserCreateDTO dto);

    /**
     * 获取管理员操作日志
     */
    Map<String, Object> getAdminLogs(LogSearchDTO dto);

    /**
     * 获取C端用户操作日志
     */
    Map<String, Object> getUserLogs(LogSearchDTO dto);

    List<AdminPostStatVO> getPostViewRanking(int limit);

    /**
     * 获取指定帖子的审核历史记录
     */
    List<PostAuditLogVO> getPostAuditHistory(String postId);

    AdminStatsVO getDataStatistics();

    void exportLogs(LogSearchDTO dto, HttpServletResponse response);
}
