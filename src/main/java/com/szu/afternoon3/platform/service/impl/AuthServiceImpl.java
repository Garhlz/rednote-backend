package com.szu.afternoon3.platform.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.szu.afternoon3.platform.VO.LoginVO;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.service.AuthService;
import com.szu.afternoon3.platform.util.JwtUtil;
import com.szu.afternoon3.platform.util.WeChatUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Override
    @Transactional(rollbackFor = Exception.class) // 事务注解：出现异常自动回滚
    public LoginVO wechatLogin(String code) {
        // 1. 调用微信接口换取 OpenID
        String openid = weChatUtil.getOpenId(code);

        // 2. 查询数据库是否存在该 OpenID
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getOpenid, openid));

        boolean isNewUser = false;

        // 3. 如果用户不存在，进行静默注册
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setNickname("微信用户"); // 默认昵称
            user.setAvatar("https://你的OSS默认头像地址.png"); // TODO: 替换为实际默认头像
            user.setRole("USER");
            user.setStatus(1);
            // 插入数据库
            userMapper.insert(user);
            isNewUser = true;
            log.info("新用户注册: id={}", user.getId());
        }

        // 4. 构建返回对象
        return buildLoginVO(user, isNewUser);
    }

    @Override
    public LoginVO accountLogin(String account, String password) {
        // 1. 根据 邮箱 或 昵称 查询用户
        // 这里的nickname就是用户注册时候选择的nickname
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, account)
                .or()
                .eq(User::getNickname, account));

        // TODO 这里直接发送定义好的错误状态码即可，当然不需要抛出异常
        // 2. 用户不存在
        if (user == null) {
            throw new RuntimeException("账号不存在");
        }
        // TODO 改为加密存储
        // 3. 校验密码 (这里暂时用明文，强烈建议后续改为 BCrypt 加密)
        if (!password.equals(user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        // 4. 账号状态检查
        if (user.getStatus() == 0) {
            throw new RuntimeException("该账号已被禁用");
        }

        return buildLoginVO(user, false);
    }

    // 辅助方法：封装 VO
    private LoginVO buildLoginVO(User user, boolean isNewUser) {
        // 生成 JWT Token
        String token = jwtUtil.createToken(user.getId());

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setIsNewUser(isNewUser);
        // 判断密码是否为空
        vo.setHasPassword(StrUtil.isNotBlank(user.getPassword()));

        LoginVO.UserInfo info = new LoginVO.UserInfo();
        info.setUserId(user.getId().toString()); // Long 转 String
        info.setNickname(user.getNickname());
        info.setAvatar(user.getAvatar());
        info.setEmail(user.getEmail());

        vo.setUserInfo(info);
        return vo;
    }
}