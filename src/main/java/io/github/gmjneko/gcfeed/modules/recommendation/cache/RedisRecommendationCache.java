package io.github.gmjneko.gcfeed.modules.recommendation.cache;

import io.github.gmjneko.gcfeed.modules.recommendation.model.RecommendationProperties;
import io.github.gmjneko.gcfeed.common.constant.RedisKeyConstant;
import io.github.gmjneko.gcfeed.modules.recommendation.model.RecommendationSnapshot;
import io.github.gmjneko.gcfeed.modules.recommendation.model.UserProfile;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * 显式字符串 JSON 编解码，避免 Redisson 默认 POJO 编解码器与 Jackson 版本耦合。
 */
@Component
public class RedisRecommendationCache implements RecommendationCache {

    private final RedissonClient redis;
    private final JsonMapper json;
    private final RecommendationProperties properties;

    public RedisRecommendationCache(RedissonClient redis, JsonMapper json, RecommendationProperties properties) {
        this.redis = redis;
        this.json = json;
        this.properties = properties;
    }

    @Override
    public Optional<UserProfile> findProfile(Long userId, String model, int dimension) {
        return read(profileKey(userId, model, dimension), UserProfile.class);
    }

    @Override
    public void saveProfile(Long userId, String model, int dimension, UserProfile profile) {
        write(profileKey(userId, model, dimension), profile, properties.profileTtlSeconds());
    }

    @Override
    public Optional<RecommendationSnapshot> findSnapshot(String id) {
        return read(snapshotKey(id), RecommendationSnapshot.class);
    }

    @Override
    public void saveSnapshot(String id, RecommendationSnapshot snapshot) {
        write(snapshotKey(id), snapshot, properties.snapshotTtlSeconds());
    }

    public static String snapshotKey(String id) {
        return RedisKeyConstant.RECOMMENDATION_SNAPSHOT.formatted(id);
    }

    public static String profileKey(Long userId, String model, int dimension) {
        // 模型名编码为一个键片段，避免其中的分隔符与维度、用户 ID 混淆。
        String space = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(model.getBytes(StandardCharsets.UTF_8));
        return RedisKeyConstant.RECOMMENDATION_PROFILE.formatted(space, dimension, userId);
    }

    private <T> Optional<T> read(String key, Class<T> type) {
        // 只读 GET，不刷新 TTL；解码异常交给 Service 按画像/快照的不同语义处理。
        String value = redis.<String>getBucket(key, StringCodec.INSTANCE).get();
        return value == null ? Optional.empty() : Optional.of(json.readValue(value, type));
    }

    private void write(String key, Object value, long ttlSeconds) {
        // 值与 TTL 一次写入，避免先 SET 再 EXPIRE 之间失败而留下永久快照。
        redis.<String>getBucket(key, StringCodec.INSTANCE)
                .set(json.writeValueAsString(value), Duration.ofSeconds(ttlSeconds));
    }

}
