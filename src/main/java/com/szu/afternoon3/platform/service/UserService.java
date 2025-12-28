package com.szu.afternoon3.platform.service;

import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.vo.*;

import java.util.List;
import java.util.Map;

public interface UserService {
    /**
     * 获取当前登录用户的个人资料
     */
    UserProfileVO getUserProfile();

    /**
     * 修改个人资料
     */
    void updateProfile(UserProfileUpdateDTO dto);

    /**
     * 绑定邮箱
     * @return 返回绑定后的邮箱信息
     */
    UserEmailVO bindEmail(UserBindEmailDTO dto);

    /**
     * 首次设置密码 (验证邮箱模式)
     */
    void setPasswordWithCode(UserPasswordSetDTO dto);

    /**
     * 修改密码 (验证旧密码)
     */
    void changePassword(UserPasswordChangeDTO dto);

    // 获取关注列表
    PageResult<SimpleUserVO> getFollowList(String userId, Integer page, Integer size);

    // 获取粉丝列表
    PageResult<SimpleUserVO> getFanList(String userId, Integer page, Integer size);

    // 关注用户
    void followUser(String targetUserId);

    // 取消关注
    void unfollowUser(String targetUserId);

    // 获取好友列表 (互相关注)
    PageResult<SimpleUserVO> getFriendList(FriendSearchDTO dto);

    /**
     * 搜索用户
     * @param keyword 昵称或邮箱关键词
     * @return 按照双向关系状态排序的用户列表
     */
    List<UserSearchVO> searchUsers(String keyword);

    // 获取我的点赞列表
    PageResult<PostVO> getMyLikeList(Integer page, Integer size);

    // 获取我的收藏列表
    PageResult<PostVO> getMyCollectList(Integer page, Integer size);

    // 获取我的评分列表
    PageResult<PostVO> getMyRateList(Integer page, Integer size);

    // 获取我的帖子
    PageResult<PostVO> getMyPostList(Integer type, Integer page, Integer size);

    // 获取浏览历史
    PageResult<PostVO> getBrowsingHistory(Integer page, Integer size);

    // 记录浏览历史 (给 PostService 调用)
    void recordBrowsingHistory(Long userId, String postId);

    // 获取我的评论列表
    PageResult<MyCommentVO> getMyCommentList(Integer page, Integer size);

    PublicUserProfileVO getPublicUserProfile(Long targetUserId);
}
