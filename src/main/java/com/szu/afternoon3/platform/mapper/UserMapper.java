package com.szu.afternoon3.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.szu.afternoon3.platform.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 基础的增删改查不用写，继承 BaseMapper 就有了
}