package io.github.gmjneko.gcfeed.common.context;

/**
 * 当前请求的用户上下文。
 *
 * <p>由 {@code JwtInterceptor} 在请求进入时写入、请求结束时清理，
 * 业务层通过 {@link #getUserId()} 直接读取当前登录用户，无需再解析 token。
 *
 * <p>使用 {@link ThreadLocal} 承载，因此不要在业务代码里开新线程访问它；
 * 需要异步处理时应在提交任务前先把 userId 取出来当参数传递。
 */
public final class UserContext {

    private UserContext() {
    }

    /** 承载用户 ID 与角色，避免多次 ThreadLocal 查询。 */
    private record Principal(Long userId, String role) {

    }

    private static final ThreadLocal<Principal> HOLDER = new ThreadLocal<>();

    /**
     * 写入当前登录用户。
     *
     * @param userId 用户 ID，必须为正数
     * @param role   用户角色，可为空
     */
    public static void set(Long userId, String role) {
        if (userId == null || userId <= 0) {
            HOLDER.remove();
            return;
        }
        HOLDER.set(new Principal(userId, role));
    }

    /**
     * 读取当前登录用户 ID。
     *
     * @return 用户 ID；未登录时返回 {@code null}
     */
    public static Long getUserId() {
        Principal principal = HOLDER.get();
        return principal == null ? null : principal.userId();
    }

    /**
     * 读取当前登录用户 ID，未登录时抛出异常。
     *
     * <p>供「必须登录」的分支使用，避免每个调用点重复判空。
     *
     * @return 用户 ID
     * @throws IllegalStateException 当前请求没有登录态
     */
    public static Long requireUserId() {
        Long userId = getUserId();
        if (userId == null) {
            throw new IllegalStateException("当前请求无登录态。");
        }
        return userId;
    }

    /**
     * 读取当前登录用户角色。
     *
     * @return 角色；未登录或未写入时返回 {@code null}
     */
    public static String getRole() {
        Principal principal = HOLDER.get();
        return principal == null ? null : principal.role();
    }

    /**
     * 判断当前是否已登录。
     */
    public static boolean isAuthenticated() {
        return getUserId() != null;
    }

    /**
     * 清理上下文。
     *
     * <p>必须在请求结束的 finally 块中调用，否则线程池复用线程时会残留上一个请求的身份。
     */
    public static void clear() {
        HOLDER.remove();
    }

}
