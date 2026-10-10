package io.github.gmjneko.gcfeed.common.jwt;

/**
 * 解析后的 JWT 载荷。
 *
 * <p>业务代码只依赖该对象，不直接接触 jjwt 的 {@code Claims}，
 * 这样将来更换 JWT 库或调整声明结构时，改动范围局限在 {@link JwtUtil} 内部。
 */
public class JwtPayload {

    /** 用户 ID，对应 JWT 的 {@code uid} 声明。 */
    private final Long userId;

    /** 用户角色，对应 {@code role} 声明。 */
    private final String role;

    /** 令牌类型，当前固定为 {@code access}。 */
    private final String tokenType;

    /** 令牌唯一标识，对应 {@code jti}，用于登出黑名单。 */
    private final String jwtId;

    /** 签发时间（Unix 秒）。 */
    private final long issuedAt;

    /** 过期时间（Unix 秒）。 */
    private final long expiresAt;

    public JwtPayload(Long userId, String role, String tokenType, String jwtId,
                      long issuedAt, long expiresAt) {
        this.userId = userId;
        this.role = role;
        this.tokenType = tokenType;
        this.jwtId = jwtId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    /**
     * 计算令牌剩余有效秒数，用于设置登出黑名单的 TTL。
     *
     * @return 剩余秒数，已过期时返回 0
     */
    public long remainingSeconds() {
        long remaining = expiresAt - (System.currentTimeMillis() / 1000L);
        return Math.max(remaining, 0L);
    }

    public Long getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String getJwtId() {
        return jwtId;
    }

    public long getIssuedAt() {
        return issuedAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

}
