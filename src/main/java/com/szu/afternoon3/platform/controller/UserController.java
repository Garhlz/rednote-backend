package com.szu.afternoon3.platform.controller;

import cn.hutool.core.util.StrUtil;
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

    /**
     * 获取粉丝列表
     * 对应 Apifox 接口: /api/user/fans/{userId} (GET)
     */
    @GetMapping("/fans/{userId}")
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
     * 用于聊天页面的联系人列表
     */
    @GetMapping("/friends")
    public Result<List<UserInfo>> getFriends() {
        List<UserInfo> list = userService.getFriendList();
        return Result.success(list);
    }

    /**
     * 搜索用户
     * 对应接口: GET /api/user/search
     */
    @GetMapping("/search")
    public Result<List<UserSearchVO>> searchUsers(@RequestParam String keyword) {
        List<UserSearchVO> list = userService.searchUsers(keyword);
        return Result.success(list);
    }

    /**
     * 获取我的点赞列表
     */
    @GetMapping("/likes")
    public Result<Map<String, Object>> getMyLikes(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(userService.getMyLikeList(page, size));
    }

    /**
     * 获取我的收藏列表
     */
    @GetMapping("/collects")
    public Result<Map<String, Object>> getMyCollects(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(userService.getMyCollectList(page, size));
    }

    /**
     * 获取我的评分列表
     */
    @GetMapping("/ratings")
    public Result<Map<String, Object>> getMyRatings(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(userService.getMyRateList(page, size));
    }

    /**
     * 获取我的帖子列表 (支持按类型筛选)
     * 对应接口: /api/user/posts
     */
    @GetMapping("/posts")
    public Result<Map<String, Object>> getMyPosts(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(userService.getMyPostList(type, page, size));
    }

    /**
     * 获取浏览历史
     * 对应接口: /api/user/history
     */
    @GetMapping("/history")
    public Result<Map<String, Object>> getHistory(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(userService.getBrowsingHistory(page, size));
    }

    @GetMapping("/comments")
    public Result<Map<String, Object>> getMyComments(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {

        return Result.success(userService.getMyCommentList(page, size));
    }
}
