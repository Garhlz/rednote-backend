package com.szu.afternoon3.platform.service;

import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.vo.UserProfileVO;

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
}
