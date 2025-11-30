-- ==========================================
-- 1. 用户表 (users) - 保留在 PostgreSQL
-- ==========================================
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    openid VARCHAR(64) UNIQUE,
    email VARCHAR(255) UNIQUE,
    password VARCHAR(255),
    nickname VARCHAR(64) NOT NULL DEFAULT '微信用户',
    avatar VARCHAR(512) DEFAULT '',
    gender SMALLINT DEFAULT 0,
    birthday DATE,
    region VARCHAR(100),
    bio VARCHAR(255) DEFAULT '',
    role VARCHAR(20) DEFAULT 'USER',
    status INT DEFAULT 1,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT DEFAULT 0
);

CREATE INDEX idx_users_openid ON users(openid);
CREATE INDEX idx_users_email ON users(email);
COMMENT ON TABLE users IS '用户核心表';

-- 其他表（posts, comments, likes等）已移除，转由 MongoDB 接管