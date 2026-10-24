package com.fridgemind.security;

/**
 * 认证上下文：基于 ThreadLocal 的请求级用户信息存储
 * 请求结束后由拦截器 afterCompletion 清理，防止内存泄漏。
 */
public class AuthContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> FAMILY_ID = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void setFamilyId(Long familyId) {
        FAMILY_ID.set(familyId);
    }

    public static Long getFamilyId() {
        return FAMILY_ID.get();
    }

    /** 清理 ThreadLocal，防止线程复用时数据残留 */
    public static void clear() {
        USER_ID.remove();
        FAMILY_ID.remove();
    }
}
