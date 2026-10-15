package com.chattheater.security;

import com.chattheater.common.BizException;
import com.chattheater.user.entity.User;

/**
 * 管理员权限守卫：controller 内调用 AdminGuard.check()。
 */
public final class AdminGuard {

    private AdminGuard() {
    }

    public static void check() {
        User user = UserContext.get();
        if (user == null || !"ADMIN".equals(user.getRole())) {
            throw BizException.forbidden("需要管理员权限");
        }
    }
}
