package com.szu.afternoon3.platform.controller;

import cn.hutool.core.util.StrUtil;
import com.szu.afternoon3.platform.annotation.OperationLog;
import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.enums.ResultCode;
import com.szu.afternoon3.platform.service.UserService;
import com.szu.afternoon3.platform.vo.*;
import jakarta.validation.Valid;
import org.apache.ibatis.javassist.Loader;
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
     * 获取他人公开主页信息
     * @param userId 目标用户ID
     */
    @GetMapping("/{userId}/profile")
    @OperationLog(module = "用户模块", description = "获取他人主页信息")
    public Result<PublicUserProfileVO> getOtherUserProfile(@PathVariable Long userId) {
        return Result.success(userService.getPublicUserProfile(userId));
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
    public Result<UserEmailVO> bindEmail(@RequestBody @Valid UserBindEmailDTO dto) {
        UserEmailVO result = userService.bindEmail(dto);
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
     * 返回值改为 PageResult<SimpleUserVO>
     */
    @GetMapping("/follows/{userId}")
    @OperationLog(module = "用户模块", description = "获取关注列表", bizId = "#userId")
    public Result<PageResult<SimpleUserVO>> getFollows(
            @PathVariable String userId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size
    ) {
        return Result.success(userService.getFollowList(userId, page, size));
    }

    /**
     * 获取我的粉丝列表
     * 返回值改为 PageResult<SimpleUserVO>
     */
    @GetMapping("/fans/{userId}")
    @OperationLog(module = "用户模块", description = "获取粉丝列表", bizId = "#userId")
    public Result<PageResult<SimpleUserVO>> getFans(
            @PathVariable String userId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size
    ) {
        return Result.success(userService.getFanList(userId, page, size));
    }

    /**
     * 关注用户
     * 入参改为 @Valid UserIdDTO
     */
    @PostMapping("/follow")
    @OperationLog(module = "用户模块", description = "关注用户", bizId = "#dto.targetUserId")
    public Result<Void> followUser(@RequestBody @Valid UserIdDTO dto) {
        userService.followUser(dto.getTargetUserId());
        return Result.success();
    }

    /**
     * 取消关注
     * 入参改为 @Valid UserIdDTO
     */
    @PostMapping("/unfollow")
    @OperationLog(module = "用户模块", description = "取消关注", bizId = "#dto.targetUserId")
    public Result<Void> unfollowUser(@RequestBody @Valid UserIdDTO dto) {
        userService.unfollowUser(dto.getTargetUserId());
        return Result.success();
    }

    /**
     * 获取好友列表 (互相关注)
     * 支持分页，支持按昵称搜索
     */
    @GetMapping("/friends")
    @OperationLog(module = "用户模块", description = "获取好友列表")
    public Result<PageResult<SimpleUserVO>> getFriends(@ModelAttribute FriendSearchDTO dto) {
        // 现在这里返回的是 Result<PageResult<UserInfo>>
        // 结构清晰：code, msg, data: { records: [], total: 100 ... }
        return Result.success(userService.getFriendList(dto));
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
     * 返回值改为 PageResult<PostVO>
     */
    @GetMapping("/likes")
    @OperationLog(module = "用户模块", description = "获取点赞列表")
    public Result<PageResult<PostVO>> getMyLikes(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(userService.getMyLikeList(page, size));
    }

    /**
     * 获取我的收藏列表
     * 返回值改为 PageResult<PostVO>
     */
    @GetMapping("/collects")
    @OperationLog(module = "用户模块", description = "获取收藏列表")
    public Result<PageResult<PostVO>> getMyCollects(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(userService.getMyCollectList(page, size));
    }

    /**
     * 获取我的评分列表
     * 返回值改为 PageResult<PostVO>
     */
    @GetMapping("/ratings")
    @OperationLog(module = "用户模块", description = "获取评分列表")
    public Result<PageResult<PostVO>> getMyRatings(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(userService.getMyRateList(page, size));
    }

    /**
     * 获取我的帖子列表
     * 返回值改为 PageResult<PostVO>
     */
    @GetMapping("/posts")
    @OperationLog(module = "用户模块", description = "获取我的帖子")
    public Result<PageResult<PostVO>> getMyPosts(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(userService.getMyPostList(type, page, size));
    }

    /**
     * 获取浏览历史
     * 返回值改为 PageResult<PostVO>
     */
    @GetMapping("/history")
    @OperationLog(module = "用户模块", description = "获取浏览历史")
    public Result<PageResult<PostVO>> getHistory(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(userService.getBrowsingHistory(page, size));
    }

    /**
     * 获取我的评论列表
     * 返回值改为 PageResult<MyCommentVO>
     */
    @GetMapping("/comments")
    @OperationLog(module = "用户模块", description = "获取我的评论")
    public Result<PageResult<MyCommentVO>> getMyComments(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(userService.getMyCommentList(page, size));
    }
}