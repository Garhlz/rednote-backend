package com.szu.afternoon3.platform.service;

import com.szu.afternoon3.platform.dto.TestUserCreateDTO;
import com.szu.afternoon3.platform.dto.UserPasswordResetDTO;
import com.szu.afternoon3.platform.vo.LoginVO;

public interface AuthService {
    LoginVO wechatLogin(String code);
    LoginVO accountLogin(String account, String password);
    void sendEmailCode(String email);
    void resetPassword(UserPasswordResetDTO dto);

    /**
     * 退出登录 (Token 加入黑名单)
     * @param token 前端传来的 JWT 字符串
     */
    void logout(String token);

    /**
     * 创建测试用户
     * @return 用户ID
     */
    Long createTestUser(TestUserCreateDTO dto);
}