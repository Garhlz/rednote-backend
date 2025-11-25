-- ==========================================
-- 1. 用户表 (users)
-- ==========================================
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    openid VARCHAR(64) UNIQUE,
    -- 微信 OpenID (唯一)
    email VARCHAR(255) UNIQUE,
    -- 邮箱 (唯一)
    password VARCHAR(255),
    -- 密码 Hash
    nickname VARCHAR(64) NOT NULL DEFAULT '微信用户',
    avatar VARCHAR(512) DEFAULT '',
    -- 头像 URL
    gender SMALLINT DEFAULT 0,
    -- 性别，0:保密, 1:男, 2:女
    birthday DATE,
    -- 生日
    region VARCHAR(100),
    -- 地区
    bio VARCHAR(255) DEFAULT '',
    -- 个人简介
    role VARCHAR(20) DEFAULT 'USER',
    -- 角色
    status INT DEFAULT 1,
    -- 1:正常, 0:禁用
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT DEFAULT 0 -- 0:未删, 1:已删
);
-- 索引：优化登录查询
CREATE INDEX idx_users_openid ON users(openid);
CREATE INDEX idx_users_email ON users(email);
COMMENT ON TABLE users IS '用户核心表';
-- ==========================================
-- 2. 帖子表 (posts)
-- ==========================================
CREATE TABLE posts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    -- 作者ID
    title VARCHAR(100) NOT NULL,
    content TEXT,
    type INT DEFAULT 0,
    -- 0:图文, 1:视频
    status INT DEFAULT 0,
    -- 0:审核中, 1:发布, 2:拒绝
    -- 冗余计数
    view_count INT DEFAULT 0,
    like_count INT DEFAULT 0,
    collect_count INT DEFAULT 0,
    -- 这里的 jsonb 仅用于存储 AI 分析的原始数据（调试用），不参与搜索业务，后续可能会删除
    ai_raw_data JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT DEFAULT 0,
    -- 外键关联用户，用户删了帖子保持物理关联要对
    CONSTRAINT fk_posts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
-- 索引：优化“查看某人的主页”
CREATE INDEX idx_posts_user_id ON posts(user_id);
COMMENT ON TABLE posts IS '帖子主表';
-- ==========================================
-- 3. 帖子资源表 (post_resources)
-- ==========================================
CREATE TABLE post_resources (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    url VARCHAR(512) NOT NULL,
    -- OSS的链接
    sort INT DEFAULT 0,
    resource_type VARCHAR(10) CHECK (resource_type IN ('IMAGE', 'VIDEO')),
    -- 约束类型
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT DEFAULT 0,
    -- 帖子物理删除时，资源自动删除
    CONSTRAINT fk_resources_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
);
-- 索引：优化“加载帖子详情”
CREATE INDEX idx_resources_post_id ON post_resources(post_id);
COMMENT ON TABLE post_resources IS '帖子图片/视频资源表';
-- ==========================================
-- 4. 互动表 (interactions)
-- ==========================================
CREATE TABLE interactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    type INT NOT NULL,
    -- 1:点赞, 2:收藏
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT DEFAULT 0,
    UNIQUE (user_id, post_id, type),
    CONSTRAINT fk_interactions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_interactions_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
);
-- 索引：查询某人点赞过的列表 / 查询某帖子的点赞列表
CREATE INDEX idx_interactions_user_type ON interactions(user_id, type);
CREATE INDEX idx_interactions_post_type ON interactions(post_id, type);
COMMENT ON TABLE interactions IS '点赞收藏互动表';
-- ==========================================
-- 5. 评论表 (comments)
-- ==========================================
CREATE TABLE comments (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    -- 【修改】改为 NULL 表示顶级评论，方便做外键约束
    parent_id BIGINT DEFAULT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT DEFAULT 0,
    CONSTRAINT fk_comments_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    -- 自引用外键：确保 parent_id 指向的是一条存在的评论
    CONSTRAINT fk_comments_parent FOREIGN KEY (parent_id) REFERENCES comments(id) ON DELETE CASCADE
);
-- 索引：查询帖子下的评论
CREATE INDEX idx_comments_post_id ON comments(post_id);
COMMENT ON TABLE comments IS '评论表';
-- ==========================================
-- 6. 标签表 (tags)
-- ==========================================
CREATE TABLE tags (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    usage_count INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_tags_usage ON tags(usage_count DESC);
-- 方便查热门标签
COMMENT ON TABLE tags IS '标签字典表';
-- ==========================================
-- 7. 帖子-标签关联表 (post_tags)
-- ==========================================
CREATE TABLE post_tags (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (post_id, tag_id),
    -- 【关键约束】帖子删了或标签删了，关联关系自动消失
    CONSTRAINT fk_post_tags_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_post_tags_tag FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
);
CREATE INDEX idx_post_tags_tag_id ON post_tags(tag_id);
-- 方便查“带有某标签的所有帖子”
COMMENT ON TABLE post_tags IS '帖子与标签的多对多关系';