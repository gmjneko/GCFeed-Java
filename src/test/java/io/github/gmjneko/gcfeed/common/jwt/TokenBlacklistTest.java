package io.github.gmjneko.gcfeed.common.jwt;

import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证过期令牌无需保存，以及吊销写入失败不会被当作成功。 */
class TokenBlacklistTest {

    @Test
    void expiredTokenDoesNotWriteRedis() {
        RedissonClient redis = mock(RedissonClient.class);
        new TokenBlacklist(redis).revoke(payload(System.currentTimeMillis() / 1000 - 1));
        verifyNoInteractions(redis);
    }

    @Test
    void revokePropagatesWriteFailure() {
        RedissonClient redis = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        when(redis.<String>getBucket("auth:blacklist:v1:jti")).thenReturn(bucket);
        when(bucket.setIfAbsent(eq("1"), any(Duration.class))).thenThrow(new IllegalStateException("write failed"));
        assertThatThrownBy(() -> new TokenBlacklist(redis).revoke(payload(System.currentTimeMillis() / 1000 + 60)))
                .isInstanceOf(IllegalStateException.class).hasMessage("write failed");
    }

    private JwtPayload payload(long expiresAt) {
        return new JwtPayload(42L, "user", "access", "jti", 0, expiresAt);
    }

}
