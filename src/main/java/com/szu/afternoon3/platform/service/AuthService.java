package com.szu.afternoon3.platform.service;

import com.szu.afternoon3.platform.VO.LoginVO;

public interface AuthService {
    LoginVO wechatLogin(String code);
    LoginVO accountLogin(String account, String password);
}