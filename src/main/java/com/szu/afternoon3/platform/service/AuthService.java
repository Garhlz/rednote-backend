package com.szu.afternoon3.platform.service;

import com.szu.afternoon3.platform.dto.UserPasswordResetDTO;
import com.szu.afternoon3.platform.vo.LoginVO;

public interface AuthService {
    LoginVO wechatLogin(String code);
    LoginVO accountLogin(String account, String password);
    void sendEmailCode(String email);
    void resetPassword(UserPasswordResetDTO dto);
}