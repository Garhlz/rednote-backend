package com.szu.afternoon3.platform.common;

/**
 * 用户上下文容器
 * 利用 ThreadLocal 实现线程隔离，存储当前登录的用户ID
 */
public class UserContext {
    private static final ThreadLocal<Long> USER_HOLDER = new ThreadLocal<>();

    // 存入 UserId
    public static void setUserId(Long userId) {
        USER_HOLDER.set(userId);
    }

    // 获取 UserId
    public static Long getUserId() {
        return USER_HOLDER.get();
    }

    // 清除
    public static void clear() {
        USER_HOLDER.remove();
    }
}