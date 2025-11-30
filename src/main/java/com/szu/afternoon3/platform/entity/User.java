package com.szu.afternoon3.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@TableName("users") // 对应数据库表名
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String openid;
    private String email;
    private String password;
    private String nickname;
    private String avatar;
    private Integer gender; // 0, 1, 2
    private LocalDate birthday;
    private String region;
    private String bio;
    private String role;     // USER, ADMIN
    private Integer status;  // 1 正常

    // 自动填充字段 (需要在 MybatisPlusConfig 中配置 Handler 才会生效，暂时手动或者数据库默认值也行)
    // 这里我们先假设数据库有 DEFAULT CURRENT_TIMESTAMP，或者手动设置
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    @TableLogic // 逻辑删除
    private Integer isDeleted;
}