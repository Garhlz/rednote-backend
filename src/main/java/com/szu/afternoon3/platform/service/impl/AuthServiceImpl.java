package com.szu.afternoon3.platform.service.impl;

import cn.hutool.core.util.RandomUtil;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatUtil weChatUtil;
    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JavaMailSender mailSender; // 注入邮件发送器

    @Autowired
    private StringRedisTemplate redisTemplate; // 注入 Redis

    @Value("${spring.mail.username}")
    private String fromEmail; // 发送人

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

    @Override
    public void sendEmailCode(String email) {
        // Redis Key 设计: verify:code:{email}
        // 例如: verify:code:test@qq.com
        String redisKey = "verify:code:" + email;

        // 1. 防刷校验 (Rate Limiting): 检查是否有 TTL，如果 TTL > 4分30秒 (说明刚发过不到30秒)，拦截
        // 或者简单点：设置一个额外的 key "verify:limit:email" 有效期 60s
        String limitKey = "verify:limit:" + email;
        if (redisTemplate.hasKey(limitKey)) {
            throw new AppException(ResultCode.OPERATION_TOO_FREQUENT);
        }

        // 2. 生成 6 位随机数字验证码
        String code = RandomUtil.randomNumbers(6);

        // 3. 发送邮件
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("【映记】验证码");
            message.setText("您的验证码是：" + code + "。有效期为5分钟，请勿泄露给他人。");
            mailSender.send(message);
            log.info("邮件发送成功: {} -> {}", email, code);
        } catch (Exception e) {
            log.error("邮件发送失败", e);
            throw new AppException(ResultCode.MAIL_SEND_ERROR);
        }

        // 4. 存入 Redis (5分钟过期)
        redisTemplate.opsForValue().set(redisKey, code, 5, TimeUnit.MINUTES);

        // 5. 设置限流 Key (60秒过期)
        redisTemplate.opsForValue().set(limitKey, "1", 60, TimeUnit.SECONDS);
    }
}