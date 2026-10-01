package com.zhiyu.security;

/**
 * 当前请求的登录用户上下文（{@link ThreadLocal}）。
 *
 * <p>由 {@link JwtAuthFilter} 在请求进入时写入、在请求结束（含异常）时
 * {@link #clear()}。业务代码<b>只读不写</b>。
 *
 * <p><b>线程池安全</b>：Tomcat 复用工作线程，若忘记 clear，上一个请求的登录用户
 * 会残留在下一个请求里，造成越权。故 clear 必须放在 {@code finally} 里。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    /** 写入当前登录用户（只应由 JwtAuthFilter 调用）。 */
    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    /** 当前登录用户；未登录（或已放行的匿名请求）返回 {@code null}。 */
    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 当前登录用户主键；未登录返回 {@code null}。 */
    public static Long getUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    /** 当前登录用户角色码；未登录返回 {@code null}。 */
    public static Integer getRole() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getRole();
    }

    /** 清理上下文。必须在请求结束时（finally）调用，否则线程池会串号。 */
    public static void clear() {
        HOLDER.remove();
    }
}
