package com.chattheater.security;

import com.chattheater.user.entity.User;

/**
 * 当前请求的登录用户上下文（拦截器写入，请求结束清除）。
 */
public final class UserContext {

    private static final ThreadLocal<User> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(User user) {
        HOLDER.set(user);
    }

    public static User get() {
        return HOLDER.get();
    }

    public static Long userId() {
        User u = HOLDER.get();
        return u == null ? null : u.getId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
