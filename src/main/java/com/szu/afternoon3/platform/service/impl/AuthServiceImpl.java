package com.szu.afternoon3.platform.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt; // 引入 Hutool 加密工具
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.szu.afternoon3.platform.exception.ResultCode;
import com.szu.afternoon3.platform.exception.AppException;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.service.AuthService;
import com.szu.afternoon3.platform.util.JwtUtil;
import com.szu.afternoon3.platform.util.WeChatUtil;
import com.szu.afternoon3.platform.vo.LoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatUtil weChatUtil;
    @Autowired
    private JwtUtil jwtUtil;

    // todo 优化默认头像路径
    // 可在 application.yml 配置 oss.default-avatar，这里先定义为常量
    // 也可以使用 @Value("${szu.oss.default-avatar}") private String defaultAvatar;
    private static final String DEFAULT_AVATAR = "https://szu-redbook.oss-cn-shenzhen.aliyuncs.com/default_avatar.png";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO wechatLogin(String code) {
        // 1. 调用微信接口换取 OpenID (若失败，WeChatUtil 会抛出异常)
        String openid = weChatUtil.getOpenId(code);

        // 2. 查询数据库是否存在该 OpenID
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getOpenid, openid));

        boolean isNewUser = false;

        // 3. 如果用户不存在，进行静默注册
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setNickname("微信用户");
            user.setAvatar(DEFAULT_AVATAR);
            user.setRole("USER");
            user.setStatus(1); // 1:正常

            userMapper.insert(user);
            isNewUser = true;
            log.info("微信新用户注册: id={}", user.getId());
        }

        // 4. 账号状态检查 (防御性编程，防止微信老用户被封禁后尝试登录)
        if (user.getStatus() == 0) {
            throw new AppException(ResultCode.ACCOUNT_BANNED);
        }

        return buildLoginVO(user, isNewUser);
    }

    @Override
    public LoginVO accountLogin(String account, String password) {
        // 1. 根据 邮箱 或 昵称 查询用户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, account)
                .or()
                .eq(User::getNickname, account));

        // 2. 用户不存在 -> 对应文档 40401
        if (user == null) {
            throw new AppException(ResultCode.USER_NOT_FOUND);
        }

        // 3. 校验密码 -> 对应文档 40003
        // 使用 BCrypt 校验 (注意：注册/修改密码时也必须用 BCrypt.hashpw 加密存入)
        if (user.getPassword() == null || !BCrypt.checkpw(password, user.getPassword())) {
            throw new AppException(ResultCode.ACCOUNT_PASSWORD_ERROR);
        }

        // 4. 账号状态检查 -> 对应文档 40301
        if (user.getStatus() == 0) {
            throw new AppException(ResultCode.ACCOUNT_BANNED);
        }

        return buildLoginVO(user, false);
    }

    /**
     * 辅助方法：构建返回给前端的 VO
     */
    private LoginVO buildLoginVO(User user, boolean isNewUser) {
        // 生成 JWT Token
        String token = jwtUtil.createToken(user.getId());

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setIsNewUser(isNewUser);
        // 判断是否已设置密码 (用于前端判断是否引导用户设置密码)
        vo.setHasPassword(StrUtil.isNotBlank(user.getPassword()));

        LoginVO.UserInfo info = new LoginVO.UserInfo();
        info.setUserId(user.getId().toString()); // 转 String 防止前端 JS 精度丢失
        info.setNickname(user.getNickname());
        info.setAvatar(user.getAvatar());
        info.setEmail(user.getEmail());

        vo.setUserInfo(info);
        return vo;
    }
}