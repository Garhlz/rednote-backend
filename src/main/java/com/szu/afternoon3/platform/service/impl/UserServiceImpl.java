package com.szu.afternoon3.platform.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.exception.AppException;
import com.szu.afternoon3.platform.exception.ResultCode;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.service.UserService;
import com.szu.afternoon3.platform.vo.UserProfileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public UserProfileVO getUserProfile() {
        User user = getCurrentUser();
        return buildProfileVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(UserProfileUpdateDTO dto) {
        User user = getCurrentUser();

        boolean changed = false;
        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname());
            changed = true;
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
            changed = true;
        }
        if (dto.getBio() != null) {
            user.setBio(dto.getBio());
            changed = true;
        }
        if (dto.getGender() != null) {
            user.setGender(dto.getGender());
            changed = true;
        }
        if (dto.getRegion() != null) {
            user.setRegion(dto.getRegion());
            changed = true;
        }
        if (dto.getBirthday() != null) {
            try {
                user.setBirthday(LocalDate.parse(dto.getBirthday(), DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                changed = true;
            } catch (Exception e) {
                throw new AppException(ResultCode.PARAM_ERROR); // 日期格式错误
            }
        }

        if (changed) {
            userMapper.updateById(user);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> bindEmail(UserBindEmailDTO dto) {
        // 1. 校验验证码 (Mock)
        verifyCode(dto.getEmail(), dto.getCode());

        // 2. 检查邮箱是否已被占用
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, dto.getEmail()));
        if (count > 0) {
            throw new AppException(ResultCode.EMAIL_ALREADY_EXISTS);
        }

        // 3. 更新用户邮箱
        User user = getCurrentUser();
        user.setEmail(dto.getEmail());
        userMapper.updateById(user);

        Map<String, String> result = new HashMap<>();
        result.put("email", user.getEmail());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPassword(UserSetPasswordSimpleDTO dto) {
        User user = getCurrentUser();
        // 校验密码强度（这里简单校验长度）
        if (dto.getPassword().length() < 6) {
            throw new AppException(ResultCode.PASSWORD_STRENGTH_ERROR);
        }

        String hashed = BCrypt.hashpw(dto.getPassword());
        user.setPassword(hashed);
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPasswordWithCode(UserPasswordSetDTO dto) {
        User user = getCurrentUser();

        // 1. 必须先绑定邮箱才能通过验证码设置密码 (逻辑上)
        // 或者这里的 code 是发给邮箱的，意味着 dto 应该包含 email?
        // Apifox 定义只有 code 和 password，说明 code 是关联到当前用户的邮箱的。
        // 所以前提是用户已经绑定了邮箱。
        if (StrUtil.isBlank(user.getEmail())) {
            // 这种情况下无法验证邮箱验证码，因为不知道发给谁（或者验证码服务里存了 session？）
            // 简单起见，如果用户没邮箱，报错
            throw new AppException(ResultCode.PARAM_ERROR); // 或者具体的 "未绑定邮箱"
        }

        // 2. 校验验证码 (Mock: 使用用户邮箱验证)
        verifyCode(user.getEmail(), dto.getCode());

        // 3. 检查是否已设置过密码 (Apifox 文档说: "未设置过密码的用户...")
        if (StrUtil.isNotBlank(user.getPassword())) {
            throw new AppException(ResultCode.PASSWORD_ALREADY_SET);
        }

        if (dto.getPassword().length() < 6) {
            throw new AppException(ResultCode.PASSWORD_STRENGTH_ERROR);
        }

        String hashed = BCrypt.hashpw(dto.getPassword());
        user.setPassword(hashed);
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(UserPasswordChangeDTO dto) {
        User user = getCurrentUser();

        // 1. 验证旧密码
        if (user.getPassword() == null || !BCrypt.checkpw(dto.getOldPassword(), user.getPassword())) {
            throw new AppException(ResultCode.OLD_PASSWORD_ERROR);
        }

        // 2. 验证新密码强度
        if (dto.getNewPassword().length() < 6) {
            throw new AppException(ResultCode.PASSWORD_STRENGTH_ERROR);
        }

        // 3. 更新密码
        String hashed = BCrypt.hashpw(dto.getNewPassword());
        user.setPassword(hashed);
        userMapper.updateById(user);
    }

    // Private Helpers

    private User getCurrentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new AppException(ResultCode.UNAUTHORIZED);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new AppException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    private UserProfileVO buildProfileVO(User user) {
        UserProfileVO vo = new UserProfileVO();
        vo.setUserId(user.getId().toString());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setBio(user.getBio());
        vo.setGender(user.getGender());
        if (user.getBirthday() != null) {
            vo.setBirthday(user.getBirthday().toString());
        }
        vo.setRegion(user.getRegion());
        vo.setEmail(user.getEmail());
        vo.setHasPassword(StrUtil.isNotBlank(user.getPassword()));
        return vo;
    }

    /**
     * 模拟验证码校验
     * 实际项目中应该注入 RedisTemplate 从 Redis 中获取验证码比对
     */
    private void verifyCode(String target, String code) {
        // TODO: 接 Redis 校验
        // 暂时 Mock: 只要 code 是 "123456" 就通过
        if (!"123456".equals(code)) {
            throw new AppException(ResultCode.VERIFY_CODE_ERROR);
        }
    }
}
