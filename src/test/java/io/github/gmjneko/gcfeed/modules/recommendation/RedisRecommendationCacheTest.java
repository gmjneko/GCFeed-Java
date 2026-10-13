package io.github.gmjneko.gcfeed.modules.recommendation;

import io.github.gmjneko.gcfeed.modules.recommendation.cache.RedisRecommendationCache;
import io.github.gmjneko.gcfeed.modules.recommendation.model.RecommendationProperties;
import io.github.gmjneko.gcfeed.modules.recommendation.model.RecommendationSnapshot;
import io.github.gmjneko.gcfeed.modules.recommendation.model.UserProfile;
import io.github.gmjneko.gcfeed.modules.recommendation.vo.RecommendationCandidateVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.Redisson;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 可选真实 Redis 契约验证：只操作随机测试键，验证 JSON 与固定 TTL，不清空数据库。 */
@EnabledIfEnvironmentVariable(named = "GCFEED_REDIS_TEST", matches = "true")
class RedisRecommendationCacheTest {

    @Test
    void realRedisRoundTripsAndExpiresWithoutReadRenewal() throws Exception {
        Config config = new Config();
        config.useSingleServer().setAddress(System.getenv().getOrDefault("GCFEED_TEST_REDIS_URL", "redis://127.0.0.1:6379"))
                .setConnectionMinimumIdleSize(1).setConnectionPoolSize(2);
        var redis = Redisson.create(config);
        String id = UUID.randomUUID().toString();
        String model = "test-" + id;
        String snapshotKey = RedisRecommendationCache.snapshotKey(id);
        String profileKey = RedisRecommendationCache.profileKey(1L, model, 2);
        try {
            var cache = new RedisRecommendationCache(redis, JsonMapper.builder().build(),
                    new RecommendationProperties(500, 200, 7, 30, 2, 2));
            var time = LocalDateTime.of(2026, 10, 13, 12, 0);
            var candidate = new RecommendationCandidateVO(4L, 1L, .25, -.2, 4, .5, "hot", time);
            var snapshot = new RecommendationSnapshot(1L, "recommend", model, 2, time, List.of(candidate));
            cache.saveSnapshot(id, snapshot);
            cache.saveProfile(1L, model, 2, new UserProfile(new double[]{.6, .8}));
            long before = redis.getBucket(snapshotKey, StringCodec.INSTANCE).remainTimeToLive();
            Thread.sleep(150);
            assertThat(cache.findSnapshot(id)).contains(snapshot);
            assertThat(cache.findProfile(1L, model, 2).orElseThrow().vector()).containsExactly(.6, .8);
            long after = redis.getBucket(snapshotKey, StringCodec.INSTANCE).remainTimeToLive();
            assertThat(before).isBetween(1L, 2000L);
            assertThat(after).isLessThan(before);
            Thread.sleep(2100);
            assertThat(cache.findSnapshot(id)).isEmpty();
            assertThat(cache.findProfile(1L, model, 2)).isEmpty();
            cache.saveProfile(1L, model, 2, UserProfile.empty());
            assertThat(cache.findProfile(1L, model, 2).orElseThrow().vector()).isEmpty();
        } finally {
            redis.getBucket(snapshotKey, StringCodec.INSTANCE).delete();
            redis.getBucket(profileKey, StringCodec.INSTANCE).delete();
            redis.shutdown();
        }
    }

}
