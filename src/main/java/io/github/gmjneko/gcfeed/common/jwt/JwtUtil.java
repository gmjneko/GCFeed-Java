package io.github.gmjneko.gcfeed.common.jwt;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;
import java.util.HexFormat;

/**
 * JWT 签发与校验工具。
 *
 * <p>声明结构与 Go 版保持一致，便于两端 token 语义对齐：
 * <ul>
 *   <li>{@code uid}：用户 ID</li>
 *   <li>{@code role}：用户角色</li>
 *   <li>{@code token_type}：令牌类型，当前只有 {@code access}</li>
 *   <li>{@code jti}：令牌唯一标识，登出时写入黑名单</li>
 *   <li>{@code iat} / {@code exp}：签发与过期时间</li>
 * </ul>
 *
 * <p>安全约束：
 * <ul>
 *   <li>只接受 HS256，显式限制算法可避免「算法降级」类攻击。</li>
 *   <li>密钥长度不足 256 bit 时 {@link Keys#hmacShaKeyFor} 会直接抛异常，启动即失败，
 *       避免误用弱密钥上线。</li>
 *   <li>签发时 {@code jti} 使用 {@link SecureRandom} 生成的 16 字节随机值。</li>
 * </ul>
 */
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /** 访问令牌类型。 */
    public static final String TOKEN_TYPE_ACCESS = "access";

    /** 声明名。 */
    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "token_type";

    /** jti 随机字节数。 */
    private static final int JWT_ID_BYTE_LENGTH = 16;

    private final SecretKey secretKey;

    private final JwtProperties properties;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 构造 JWT 工具。
     *
     * @param properties JWT 配置
     * @throws IllegalStateException 密钥为空或长度不足 256 bit
     */
    public JwtUtil(JwtProperties properties) {
        this.properties = properties;

        String secret = properties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("gcfeed.jwt.secret 未配置，无法启动");
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // HS256 要求密钥至少 256 bit，这里提前给出可读的提示而不是让 jjwt 抛底层异常。
            throw new IllegalStateException(
                    "gcfeed.jwt.secret 长度不足：HS256 要求至少 32 字节（当前 %d 字节）"
                            .formatted(keyBytes.length));
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 签发访问令牌。
     *
     * @param userId 用户 ID，必须为正数
     * @param role   用户角色，为空时按普通用户处理
     * @return 签名后的 JWT 字符串
     */
    public String signAccessToken(Long userId, String role) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_USER_ID);
        }

        String normalizedRole = (role == null || role.isBlank()) ? "user" : role.trim();
        long now = System.currentTimeMillis();
        Date issuedAt = new Date(now);
        Date expiresAt = new Date(now + properties.getAccessTtlSeconds() * 1000L);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer(properties.getIssuer())
                .id(randomJwtId())
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLE, normalizedRole)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 返回访问令牌有效期（秒），登录响应需要回显给客户端。
     */
    public long getAccessTtlSeconds() {
        return properties.getAccessTtlSeconds();
    }

    /**
     * 解析并校验访问令牌。
     *
     * <p>校验内容：签名、有效期、令牌类型、用户 ID 合法性。
     *
     * @param token 待解析的 JWT
     * @return 解析后的载荷
     * @throws BusinessException 令牌缺失、格式错误、已过期或类型不符
     */
    public JwtPayload parseAccessToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "access token is required");
        }

        try {
            Jws<Claims> signedClaims = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token.trim());
            Claims claims = signedClaims.getPayload();
            String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
            Long userId = resolveUserId(claims);
            if (!"HS256".equals(signedClaims.getHeader().getAlgorithm())
                    || !TOKEN_TYPE_ACCESS.equals(tokenType) || userId == null
                    || claims.getId() == null || claims.getId().isBlank()
                    || claims.getExpiration() == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "invalid access token");
            }
            return new JwtPayload(userId, claims.get(CLAIM_ROLE, String.class), tokenType,
                    claims.getId(), toEpochSecond(claims.getIssuedAt()), toEpochSecond(claims.getExpiration()));
        } catch (JwtException | IllegalArgumentException ex) {
            // 不把底层异常信息返回给客户端，也不记录原始令牌。
            log.debug("JWT 解析失败: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "invalid access token");
        }
    }

    /**
     * 从 {@code uid} 声明读取用户 ID。
     *
     * <p>jjwt 反序列化数字时可能得到 {@link Integer} 或 {@link Long}，
     * 这里统一按 {@link Number} 取值，避免大 ID 溢出。
     */
    private Long resolveUserId(Claims claims) {
        Object raw = claims.get(CLAIM_USER_ID);
        if (raw instanceof Number number) {
            long value = number.longValue();
            return value > 0 ? value : null;
        }
        if (raw instanceof String text) {
            try {
                long value = Long.parseLong(text.trim());
                return value > 0 ? value : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private long toEpochSecond(Date date) {
        return date == null ? 0L : date.getTime() / 1000L;
    }

    /**
     * 生成十六进制随机 jti。
     */
    private String randomJwtId() {
        byte[] buffer = new byte[JWT_ID_BYTE_LENGTH];
        secureRandom.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }

}
