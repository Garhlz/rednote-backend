package com.szu.afternoon3.platform.common;

/**
 * 用户上下文容器
 * 利用 ThreadLocal 实现线程隔离，存储当前登录的用户ID
 */
public class UserContext {
    private static final ThreadLocal<Long> USER_HOLDER = new ThreadLocal<>();
    // 【新增】存储当前用户的角色
    private static final ThreadLocal<String> ROLE_HOLDER = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_HOLDER.set(userId);
    }

    public static Long getUserId() {
        return USER_HOLDER.get();
    }

    // 【新增】
    public static void setRole(String role) {
        ROLE_HOLDER.set(role);
    }

    // 【新增】
    public static String getRole() {
        return ROLE_HOLDER.get();
    }

    public static void clear() {
        USER_HOLDER.remove();
        ROLE_HOLDER.remove(); // 【新增】
    }
}