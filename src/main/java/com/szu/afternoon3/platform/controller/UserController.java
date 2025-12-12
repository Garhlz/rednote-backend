package com.szu.afternoon3.platform.controller;

import cn.hutool.core.util.StrUtil;
import com.szu.afternoon3.platform.annotation.OperationLog;
import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.enums.ResultCode;
import com.szu.afternoon3.platform.service.UserService;
import com.szu.afternoon3.platform.vo.UserInfo;
import com.szu.afternoon3.platform.vo.UserProfileVO;
import com.szu.afternoon3.platform.vo.UserSearchVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户控制器
 * 处理个人资料、关注粉丝、我的收藏等业务
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 获取我的个人资料
     */
    @GetMapping("/profile")
    @OperationLog(module = "用户模块", description = "获取个人资料")
    public Result<UserProfileVO> getProfile() {
        UserProfileVO vo = userService.getUserProfile();
        return Result.success(vo);
    }

    /**
     * 修改个人资料
     */
    @PutMapping("/profile")
    @OperationLog(module = "用户模块", description = "修改个人资料")
    public Result<Void> updateProfile(@RequestBody @Valid UserProfileUpdateDTO dto) {
        userService.updateProfile(dto);
        return Result.success(null);
    }

    /**
     * 绑定邮箱
     */
    @PostMapping("/bind-email")
    @OperationLog(module = "用户模块", description = "绑定邮箱", bizId = "#dto.email")
    public Result<Map<String, String>> bindEmail(@RequestBody @Valid UserBindEmailDTO dto) {
        Map<String, String> result = userService.bindEmail(dto);
        return Result.success(result);
    }

    /**
     * 首次设置密码
     */
    @PostMapping("/password/set")
    @OperationLog(module = "用户模块", description = "设置密码")
    public Result<Void> setPasswordWithCode(@RequestBody @Valid UserPasswordSetDTO dto) {
        userService.setPasswordWithCode(dto);
        return Result.success(null);
    }

    /**
     * 修改密码
     */
    @PostMapping("/password/change")
    @OperationLog(module = "用户模块", description = "修改密码")
    public Result<Void> changePassword(@RequestBody @Valid UserPasswordChangeDTO dto) {
        userService.changePassword(dto);
        return Result.success(null);
    }

    /**
     * 获取我的关注列表
     */
    @GetMapping("/follows/{userId}")
    @OperationLog(module = "用户模块", description = "获取关注列表", bizId = "#userId")
    public Result<Map<String, Object>> getFollows(
            @PathVariable String userId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size
    ) {
        Map<String, Object> data = userService.getFollowList(userId, page, size);
        return Result.success(data);
    }

    /**
     * 获取我的粉丝列表
     */
    @GetMapping("/fans/{userId}")
    @OperationLog(module = "用户模块", description = "获取粉丝列表", bizId = "#userId")
    public Result<Map<String, Object>> getFans(
            @PathVariable String userId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size
    ) {
        Map<String, Object> data = userService.getFanList(userId, page, size);
        return Result.success(data);
    }

    /**
     * 关注用户
     */
    @PostMapping("/follow")
    @OperationLog(module = "用户模块", description = "关注用户", bizId = "#params['targetUserId']")
    public Result<Void> followUser(@RequestBody Map<String, String> params) {
        String targetUserId = params.get("targetUserId");
        if (StrUtil.isBlank(targetUserId)) {
            return Result.error(ResultCode.PARAM_ERROR);
        }
        userService.followUser(targetUserId);
        return Result.success();
    }

    /**
     * 取消关注
     */
    @PostMapping("/unfollow")
    @OperationLog(module = "用户模块", description = "取消关注", bizId = "#params['targetUserId']")
    public Result<Void> unfollowUser(@RequestBody Map<String, String> params) {
        String targetUserId = params.get("targetUserId");
        if (StrUtil.isBlank(targetUserId)) {
            return Result.error(ResultCode.PARAM_ERROR);
        }
        userService.unfollowUser(targetUserId);
        return Result.success();
    }

    /**
     * 获取好友列表 (互相关注)
     */
    @GetMapping("/friends")
    @OperationLog(module = "用户模块", description = "获取好友列表")
    public Result<List<UserInfo>> getFriends() {
        List<UserInfo> list = userService.getFriendList();
        return Result.success(list);
    }

    /**
     * 搜索用户
     */
    @GetMapping("/search")
    @OperationLog(module = "用户模块", description = "搜索用户", bizId = "#keyword")
    public Result<List<UserSearchVO>> searchUsers(@RequestParam String keyword) {
        List<UserSearchVO> list = userService.searchUsers(keyword);
        return Result.success(list);
    }

    /**
     * 获取我的点赞列表
     */
    @GetMapping("/likes")
    @OperationLog(module = "用户模块", description = "获取点赞列表")
    public Result<Map<String, Object>> getMyLikes(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(userService.getMyLikeList(page, size));
    }

    /**
     * 获取我的收藏列表
     */
    @GetMapping("/collects")
    @OperationLog(module = "用户模块", description = "获取收藏列表")
    public Result<Map<String, Object>> getMyCollects(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(userService.getMyCollectList(page, size));
    }

    /**
     * 获取我的评分列表
     */
    @GetMapping("/ratings")
    @OperationLog(module = "用户模块", description = "获取评分列表")
    public Result<Map<String, Object>> getMyRatings(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(userService.getMyRateList(page, size));
    }

    /**
     * 获取我的帖子列表
     */
    @GetMapping("/posts")
    @OperationLog(module = "用户模块", description = "获取我的帖子")
    public Result<Map<String, Object>> getMyPosts(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(userService.getMyPostList(type, page, size));
    }

    /**
     * 获取浏览历史
     */
    @GetMapping("/history")
    @OperationLog(module = "用户模块", description = "获取浏览历史")
    public Result<Map<String, Object>> getHistory(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(userService.getBrowsingHistory(page, size));
    }

    /**
     * 获取我的评论列表
     */
    @GetMapping("/comments")
    @OperationLog(module = "用户模块", description = "获取我的评论")
    public Result<Map<String, Object>> getMyComments(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {

        return Result.success(userService.getMyCommentList(page, size));
    }
}