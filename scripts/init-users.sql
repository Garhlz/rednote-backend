-- 切换/创建数据库
CREATE DATABASE IF NOT EXISTS `user_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `user_db`;

-- 清理旧表（如果存在），方便反复重置测试环境
DROP TABLE IF EXISTS `users`;

-- 创建用户核心表
CREATE TABLE `users` (
                         `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                         `email` VARCHAR(255) NOT NULL COMMENT '邮箱',
                         `password` VARCHAR(255) NOT NULL COMMENT '密码 (bcrypt加密后)',
                         `nickname` VARCHAR(64) NOT NULL DEFAULT '新用户' COMMENT '昵称',
                         `avatar` VARCHAR(512) DEFAULT '' COMMENT '头像URL',
                         `gender` TINYINT DEFAULT 0 COMMENT '性别: 0-未知, 1-男, 2-女',
                         `birthday` DATE COMMENT '生日',
                         `region` VARCHAR(100) COMMENT '地区',
                         `bio` VARCHAR(255) DEFAULT '' COMMENT '个人简介',
                         `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色: USER, ADMIN',
                         `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-正常, 0-禁用',
                         `token_version` INT NOT NULL DEFAULT 0 COMMENT 'JWT token版本号 (登出/改密码时自增)',
                         `password_changed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '密码最后修改时间',
                         `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                         `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
                         PRIMARY KEY (`id`),
                         UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户核心表';

-- 顺手插入一个管理员测试账号，密码为 123456 (假设使用 bcrypt 生成)
-- 这样你一启动容器就可以直接测登录接口
INSERT INTO `users` (`email`, `password`, `nickname`, `role`)
VALUES ('admin@test.com', '$2a$10$aAG828PAonJ4VPsyNiML.uup9B2pPbgLQhZT5xJMCZ.ZPJB6SUR9m', 'Admin', 'ADMIN');