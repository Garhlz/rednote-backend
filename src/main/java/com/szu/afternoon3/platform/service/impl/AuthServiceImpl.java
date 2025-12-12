package com.szu.afternoon3.platform.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt; // 引入 Hutool 加密工具
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.szu.afternoon3.platform.dto.TestUserCreateDTO;
import com.szu.afternoon3.platform.dto.UserPasswordResetDTO;
import com.szu.afternoon3.platform.enums.ResultCode;
import com.szu.afternoon3.platform.exception.AppException;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.service.AuthService;
import com.szu.afternoon3.platform.util.JwtUtil;
import com.szu.afternoon3.platform.util.WeChatUtil;
import com.szu.afternoon3.platform.vo.LoginVO;
import com.szu.afternoon3.platform.vo.UserInfo;
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

    @Autowired
    private com.szu.afternoon3.platform.util.TencentImUtil tencentImUtil;

    @Value("${spring.mail.username}")
    private String fromEmail; // 发送人

    // todo 优化默认头像路径
    // 可在 application.yml 配置 oss.default-avatar，这里先定义为常量
    @Value("${szu.oss.default-avatar}") private String defaultAvatar;
//    private static final String DEFAULT_AVATAR = "https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg";

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
            user.setAvatar(defaultAvatar);
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
        String token = jwtUtil.createToken(user.getId(), user.getRole());

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setIsNewUser(isNewUser);
        // 判断是否已设置密码 (用于前端判断是否引导用户设置密码)
        vo.setHasPassword(StrUtil.isNotBlank(user.getPassword()));

        UserInfo info = new UserInfo();
        info.setUserId(user.getId().toString()); // 转 String 防止前端 JS 精度丢失
        info.setNickname(user.getNickname());
        info.setAvatar(user.getAvatar());
        info.setEmail(user.getEmail());

        vo.setUserInfo(info);

        // 【新增】生成 UserSig
        // 注意：将 Long 类型的 ID 转为 String，保证和前端传给 TIM 的 userID 一致
        String userSig = tencentImUtil.genUserSig(user.getId().toString());
        vo.setUserSig(userSig);

        return vo;
    }

    @Override
    public void sendEmailCode(String email) {
        // Redis Key 设计
        String redisKey = "verify:code:" + email;
        String limitKey = "verify:limit:" + email;

        // 1. [核心修复] 防刷校验 (Rate Limiting) - 原子操作
        // 尝试设置占位符，有效期 60 秒。
        // 如果 Key 不存在，设置成功返回 true；如果 Key 已存在，设置失败返回 false。
        Boolean isAllowed = redisTemplate.opsForValue().setIfAbsent(limitKey, "1", 60, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(isAllowed)) {
            // 抛出 429 操作太频繁
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
            // 发送失败时，务必删除限流 Key，否则用户 60秒内无法重试
            redisTemplate.delete(limitKey);
            log.error("邮件发送失败", e);
            throw new AppException(ResultCode.MAIL_SEND_ERROR);
        }

        // 4. 存入 Redis (5分钟过期)
        // 只有邮件发送成功才存码
        redisTemplate.opsForValue().set(redisKey, code, 5, TimeUnit.MINUTES);
    }

    @Override
    public void resetPassword(UserPasswordResetDTO dto) {
        // 1. 校验验证码 (复用之前的逻辑)
        String redisKey = "verify:code:" + dto.getEmail();
        String cacheCode = redisTemplate.opsForValue().get(redisKey);
        if (cacheCode == null || !cacheCode.equals(dto.getCode())) {
            throw new AppException(ResultCode.VERIFY_CODE_ERROR);
        }

        // 2. 根据邮箱查找用户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, dto.getEmail()));

        if (user == null) {
            throw new AppException(ResultCode.USER_NOT_FOUND);
        }

        // 3. 加密并更新密码
        String hashed = BCrypt.hashpw(dto.getNewPassword());
        user.setPassword(hashed);
        userMapper.updateById(user);

        // 4. (可选) 删除验证码，防止二次使用
        redisTemplate.delete(redisKey);
    }

    // 定义 Redis Key 前缀
    private static final String TOKEN_BLOCK_PREFIX = "auth:token:block:";

    @Override
    public void logout(String token) {
        try {
            // 1. 解析 Token
            JWT jwt = JWTUtil.parseToken(token);

            // 2. 获取过期时间 (exp 是秒级时间戳)
            Object expObj = jwt.getPayload("exp");
            if (expObj == null) {
                return; // 没有过期时间，或者已经非法，直接忽略
            }

            long expTs = Long.parseLong(expObj.toString());
            long nowTs = System.currentTimeMillis() / 1000;
            long ttl = expTs - nowTs;

            // 3. 如果 Token 还没过期，就把它加入黑名单
            // Key: auth:token:block:{token_string}
            // Value: 1
            // Time: 剩余有效期 (ttl)
            if (ttl > 0) {
                redisTemplate.opsForValue().set(TOKEN_BLOCK_PREFIX + token, "1", ttl, TimeUnit.SECONDS);
                log.info("Token 已加入黑名单，剩余有效期: {}秒", ttl);
            }

        } catch (Exception e) {
            log.warn("退出登录处理失败: {}", e.getMessage());
            // 退出登录即使失败也不阻断用户，通常静默处理
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTestUser(TestUserCreateDTO dto) {
        // 1. 查重
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, dto.getEmail()));
        if (count > 0) {
            throw new AppException(ResultCode.EMAIL_ALREADY_EXISTS, "该测试邮箱已存在");
        }

        // 2. 插入用户
        User user = new User();
        user.setEmail(dto.getEmail());
        user.setNickname(dto.getNickname());
        // 务必加密密码
        user.setPassword(BCrypt.hashpw(dto.getPassword()));
        user.setAvatar(defaultAvatar);
        user.setRole("USER");
        user.setStatus(1);

        // 为了测试方便，随便给个 openid，防止唯一索引冲突
        user.setOpenid("test_openid_" + System.currentTimeMillis());

        userMapper.insert(user);
        return user.getId();
    }
}