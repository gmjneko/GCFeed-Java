package io.github.gmjneko.gcfeed.common.jwt;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;

/**
 * 令牌黑名单。
 *
 * <p>JWT 是无状态令牌，服务端无法直接吊销，因此登出时把令牌的 {@code jti}
 * 写入 Redis 黑名单，TTL 设为令牌剩余有效期 —— 过期后自动清理，无需人工回收。
 *
 * <p>拦截器在解析成功后查一次黑名单；Redis 不可用时按「放行」处理，
 * 保证认证这一核心链路不被缓存故障拖垮（可用性优先，安全边界依赖 token 自身的有效期兜底）。
 */
public class TokenBlacklist {

    /** Redis 客户端，可能为空（未配置 Redis 时降级为「不校验黑名单」）。 */
    private final RedissonClient redissonClient;

    /**
     * 构造令牌黑名单。
     *
     * <p>使用 {@link ObjectProvider} 延迟获取 Redis 客户端，
     * 这样在没有 Redis 的环境下（例如单元测试）依然可以启动。
     *
     * @param redissonClientProvider Redis 客户端提供者
     */
    public TokenBlacklist(ObjectProvider<RedissonClient> redissonClientProvider) {
        this.redissonClient = redissonClientProvider.getIfAvailable();
    }

    /**
     * 把令牌加入黑名单。
     *
     * @param payload 解析后的令牌载荷
     */
    public void revoke(JwtPayload payload) {
        if (redissonClient == null || payload == null || payload.getJwtId() == null) {
            return;
        }
        long ttlSeconds = payload.remainingSeconds();
        if (ttlSeconds <= 0) {
            // 令牌已过期，无需再占一个 key。
            return;
        }

        String key = BlacklistKey.of(payload.getJwtId());
        redissonClient.<String>getBucket(key).set("1", Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 判断令牌是否已被吊销。
     *
     * @param payload 解析后的令牌载荷
     * @return 已吊销返回 {@code true}
     */
    public boolean isRevoked(JwtPayload payload) {
        if (redissonClient == null || payload == null || payload.getJwtId() == null) {
            return false;
        }

        RBucket<String> bucket = redissonClient.getBucket(BlacklistKey.of(payload.getJwtId()));
        try {
            return bucket.isExists();
        } catch (RuntimeException ex) {
            // Redis 异常时放行，避免缓存故障导致全员无法登录访问。
            return false;
        }
    }

    /**
     * 校验令牌未被吊销，已吊销则抛出 401。
     *
     * @param payload 解析后的令牌载荷
     * @throws BusinessException 令牌已被吊销
     */
    public void assertNotRevoked(JwtPayload payload) {
        if (isRevoked(payload)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "access token has been revoked");
        }
    }

    /**
     * 黑名单键构造。
     *
     * <p>单独成类是为了让键格式与 {@code RedisKeyConstant.AUTH_BLACKLIST} 保持一处定义。
     */
    private static final class BlacklistKey {

        private BlacklistKey() {
        }

        private static String of(String jwtId) {
            return "auth:blacklist:v1:" + jwtId;
        }

    }

}
