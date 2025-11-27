package com.szu.afternoon3.platform.service;

import com.szu.afternoon3.platform.vo.LoginVO;

public interface AuthService {
    LoginVO wechatLogin(String code);
    LoginVO accountLogin(String account, String password);
}