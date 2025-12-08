package com.szu.afternoon3.platform.service;

import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.vo.UserInfo;
import com.szu.afternoon3.platform.vo.UserProfileVO;
import com.szu.afternoon3.platform.vo.UserSearchVO;

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
     * 
     * @return 返回绑定的邮箱
     */
    Map<String, String> bindEmail(UserBindEmailDTO dto);

    /**
     * 首次设置密码 (验证邮箱模式)
     */
    void setPasswordWithCode(UserPasswordSetDTO dto);

    /**
     * 修改密码 (验证旧密码)
     */
    void changePassword(UserPasswordChangeDTO dto);

    // 获取关注列表
    Map<String, Object> getFollowList(String userId, Integer page, Integer size);

    // 获取粉丝列表
    Map<String, Object> getFanList(String userId, Integer page, Integer size);

    // 关注用户
    void followUser(String targetUserId);

    // 取消关注
    void unfollowUser(String targetUserId);

    // 获取好友列表 (互相关注)
    List<UserInfo> getFriendList();

    /**
     * 搜索用户
     * @param keyword 昵称或邮箱关键词
     * @return 包含双向关系状态的用户列表
     */
    List<UserSearchVO> searchUsers(String keyword);
}
