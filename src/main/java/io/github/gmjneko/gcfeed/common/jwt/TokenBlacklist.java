package io.github.gmjneko.gcfeed.common.jwt;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.constant.RedisKeyConstant;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Objects;

/**
 * 使用 Redis 保存已吊销的 jti，存活时间截至令牌到期。
 *
 * <p>Redis 故障必须向上抛出，不能让已登出的令牌因黑名单读取失败而重新有效。
 */
public class TokenBlacklist {

    private final RedissonClient redissonClient;

    public TokenBlacklist(RedissonClient redissonClient) {
        this.redissonClient = Objects.requireNonNull(redissonClient);
    }

    /** 写入吊销状态；重复操作不会延长第一次吊销记录的有效期。 */
    public void revoke(JwtPayload payload) {
        long ttlSeconds = payload.remainingSeconds();
        if (ttlSeconds > 0) {
            redissonClient.<String>getBucket(key(payload))
                    .setIfAbsent("1", Duration.ofSeconds(ttlSeconds));
        }
    }

    /** 查询吊销状态；Redis 不可用时不降级为未吊销。 */
    public boolean isRevoked(JwtPayload payload) {
        return redissonClient.getBucket(key(payload)).isExists();
    }

    /** 拒绝使用已吊销令牌的受保护请求。 */
    public void assertNotRevoked(JwtPayload payload) {
        if (isRevoked(payload)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "access token has been revoked");
        }
    }

    private String key(JwtPayload payload) {
        if (payload.getJwtId() == null || payload.getJwtId().isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "invalid access token");
        }
        return RedisKeyConstant.AUTH_BLACKLIST.formatted(payload.getJwtId());
    }

}
