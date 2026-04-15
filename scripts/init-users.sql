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
                         `password_changed_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '密码最后修改时间',
                         `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
                         `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
                         `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
                         PRIMARY KEY (`id`),
                         UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户核心表';

-- 从根目录 postgres-data.sql 中迁移出来的用户数据。
-- 这里已经手工转换成 MySQL 可执行的 INSERT 语法，方便容器初始化时直接导入。
INSERT INTO `users` (
    `id`, `email`, `password`, `nickname`, `avatar`, `gender`, `birthday`, `region`, `bio`,
    `role`, `status`, `is_deleted`, `created_at`, `updated_at`, `token_version`, `password_changed_at`
) VALUES
    (11, 'test2@szu.edu.cn', '$2a$10$AnA40ijXKAiX0iUgxjBwS.e9TMaP5m8LPALBfjCebO9H1zy/vOTuK', '冒烟测试员', 'https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg', 0, NULL, NULL, '', 'USER', 1, 0, '2025-12-01 10:33:21.270698', '2025-12-01 10:33:21.270698', 0, '2026-01-21 19:40:36.592255'),
    (13, 'garhlz257@163.com', '123456', 'test', 'https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg', 0, NULL, NULL, '', 'USER', 1, 0, '2025-12-01 11:05:49.037042', '2025-12-01 11:05:49.037042', 0, '2026-01-21 19:40:36.592255'),
    (15, 'gztmft_fgo84@vip.qq.com', '$2a$10$5OBj/Pc4HwtabDFb0/KdfeAXW5A8oB7LbsfNNO.HgGXwVHB1h7awu', '杭梓馨', 'https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg', 0, NULL, NULL, '', 'USER', 1, 0, '2025-12-03 19:46:59.389696', '2025-12-03 19:46:59.392145', 0, '2026-01-21 19:40:36.592255'),
    (16, 'test4@test.com', '$2a$10$aAG828PAonJ4VPsyNiML.uup9B2pPbgLQhZT5xJMCZ.ZPJB6SUR9m', '杭梓馨', 'https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg', 0, NULL, NULL, '', 'USER', 1, 0, '2025-12-03 19:47:21.598345', '2025-12-03 19:47:21.598424', 0, '2026-01-21 19:40:36.592255'),
    (17, 'test5@test.com', '$2a$10$iMN1NCwC2F8t3t3DyS6GOO.Hw3kIHlccMY7HPds98duOEtgsaDtLy', 'eglntn', 'https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/uploads/2026/01/29/533a01f1664d463f888658d319e70af9.jpg', 0, NULL, '其他', '国家一级退堂鼓表演艺术家 🥁。主业：在 Deadline 边缘疯狂试探；副业：制造 Bug 并假装没看见 🐛。梦想是不劳而获（划掉）财富自由 💰。关注我，一起快乐摸鱼 🐟。', 'USER', 1, 0, '2025-12-03 20:03:01.553754', '2025-12-03 20:03:01.555836', 0, '2026-01-21 19:40:36.592255'),
    (275, 'admin1@test.com', '$2a$10$CcO2oC3cPY7p0yTbKRkSKuEIdYfRfGOGyw2to1QP9TgjK9FDzAHvS', '小沁', 'https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/uploads/2026/01/29/e11857d9f4ef471ea2e59deb1bbf2d7e.jpg', 0, '2018-09-01', NULL, '你好哇！我是小沁！！', 'ADMIN', 1, 0, '2025-12-12 17:34:23.700905', '2025-12-12 17:34:23.702853', 0, '2026-01-21 19:40:36.592255'),
    (276, 'ai_bot@szu.edu.cn', 'random_password_cannot_login', 'AI省流助手', 'https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg', 0, NULL, NULL, '', 'ADMIN', 1, 0, '2025-12-12 19:52:43.905878', '2025-12-12 19:52:43.905878', 0, '2026-01-21 19:40:36.592255'),
    (282, 'tech@test.com', '$2a$10$aBCvOAfUkCw6AldeO4RC7O5WV3I26feydsKS3TFcduD9pafa.1Qa6', 'TechLover', 'https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/uploads/2026/01/29/10595164385e4b9aa03353698c4d6e46.jpg', 2, '2005-06-07', '深圳', 'hello, world!', 'USER', 1, 0, '2025-12-13 14:48:30.802542', '2025-12-13 14:48:30.804403', 2, '2026-01-29 23:22:03.978008'),
    (283, 'photo@test.com', '$2a$10$rT/xOkHVCfdPbi31IvJhkeJs457F.fUy4kmfXZFmelNQB4mLAOj0q', 'PhotoLife', 'https://api.dicebear.com/7.x/avataaars/svg?seed=PhotoLife', 0, NULL, NULL, '用镜头记录生活的美好', 'USER', 1, 0, '2025-12-13 14:48:30.922389', '2025-12-13 14:48:30.922441', 0, '2026-01-21 19:40:36.592255'),
    (284, 'jane@test.com', '$2a$10$lD5vuQXBUiPF2JniDNONfeS8wlRraFM92VwyQhNOlPDWfBl1uUcyO', 'FoodieJane', 'https://api.dicebear.com/7.x/avataaars/svg?seed=FoodieJane', 0, '1998-02-27', '深圳', '探店达人 | 寻找城市角落的美味', 'USER', 1, 0, '2025-12-13 14:48:31.021005', '2025-12-13 14:48:31.021044', 0, '2026-01-21 19:40:36.592255'),
    (285, 'mike@test.com', '$2a$10$FM/4.Li8JNKbAVBKPifgF.s8kl7tLFlEGYof84mVnfTMLPs/MF9sK', 'StudentMike', 'https://api.dicebear.com/7.x/avataaars/svg?seed=StudentMike', 0, NULL, NULL, '大三学生，正在准备考研', 'USER', 1, 0, '2025-12-13 14:48:31.122688', '2025-12-13 14:48:31.122721', 0, '2026-01-21 19:40:36.592255'),
    (286, 'cat@test.com', '$2a$10$6Pbfc3qj6iuCLc.9iFog4.UyKxzVaJlUWAWnUQt6hlL53Qm6aoBTi', 'CatLover', 'https://api.dicebear.com/7.x/avataaars/svg?seed=CatLover', 1, '1980-06-12', NULL, '猫奴一枚，只有猫猫能治愈我', 'USER', 1, 0, '2025-12-13 14:48:31.225087', '2025-12-13 14:48:31.225124', 0, '2026-01-21 19:40:36.592255'),
    (288, 'Eglantine235711@gmail.com', '$2a$10$ftX1W1uz0r0hcTCbUl.dJOlr4wf1FtC801dIc17MO0eDhZJbipu4i', '测试账号', 'https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg', 0, NULL, NULL, '', 'USER', 1, 0, '2026-01-22 18:41:27.687539', '2026-01-22 18:41:27.687539', 4, '2026-01-22 23:57:23.605976'),
    (289, 'garhlz257@gmail.com', '$2a$10$llXpUqCQUe6EHSw4MprWteoT1RIVN1HgHw85M1OOizTnKhHSQdG92', 'Eglantine', 'https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg', 0, NULL, NULL, '', 'USER', 1, 0, '2026-01-23 20:31:15.617273', '2026-01-23 20:31:15.617273', 1, '2026-01-23 20:32:50.009360');

-- 明确重置自增起点，保持和原 PostgreSQL dump 的序列值一致。
ALTER TABLE `users` AUTO_INCREMENT = 290;
