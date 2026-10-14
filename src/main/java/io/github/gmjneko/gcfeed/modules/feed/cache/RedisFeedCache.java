package io.github.gmjneko.gcfeed.modules.feed.cache;

import io.github.gmjneko.gcfeed.common.constant.RedisKeyConstant;
import org.redisson.api.RBatch;
import org.redisson.api.RBuckets;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 用字符串 JSON 保存页、卡片和计数，避免 Redisson 默认对象编码和 Jackson 版本绑在一起。 */
@Component
public class RedisFeedCache implements FeedCache {

    private static final Logger log = LoggerFactory.getLogger(RedisFeedCache.class);

    private final RedissonClient redis;
    private final JsonMapper json;

    public RedisFeedCache(RedissonClient redis, JsonMapper json) {
        this.redis = redis;
        this.json = json;
    }

    @Override
    public Optional<CachedTimelinePage> findPage(String key) {
        try {
            String value = redis.<String>getBucket(key, StringCodec.INSTANCE).get();
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(json.readValue(value, CachedTimelinePage.class));
        } catch (RuntimeException ex) {
            log.warn("读取 Feed 页缓存失败，改为查询数据库 key={}: {}", key, ex.toString());
            return Optional.empty();
        }
    }

    @Override
    public void savePage(String key, CachedTimelinePage page, Duration ttl) {
        try {
            redis.<String>getBucket(key, StringCodec.INSTANCE).set(json.writeValueAsString(page), ttl);
        } catch (RuntimeException ex) {
            log.warn("写入 Feed 页缓存失败 key={}: {}", key, ex.toString());
        }
    }

    @Override
    public Map<Long, FeedCardCache> findCards(List<Long> videoIds) {
        return readMany(videoIds, FeedCacheKeys::cardKey, FeedCardCache.class, "卡片");
    }

    @Override
    public void saveCards(Collection<FeedCardCache> cards) {
        if (cards == null || cards.isEmpty()) {
            return;
        }
        Map<String, String> values = new HashMap<>();
        for (FeedCardCache card : cards) {
            if (card != null && card.videoId() > 0) {
                values.put(FeedCacheKeys.cardKey(card.videoId()), json.writeValueAsString(card));
            }
        }
        writeMany(values, Duration.ofSeconds(RedisKeyConstant.TTL_VIDEO_CARD_SECONDS), "卡片");
    }

    @Override
    public Map<Long, CachedVideoStat> findStats(List<Long> videoIds) {
        return readMany(videoIds, FeedCacheKeys::statKey, CachedVideoStat.class, "计数");
    }

    @Override
    public void saveStats(Map<Long, CachedVideoStat> stats) {
        if (stats == null || stats.isEmpty()) {
            return;
        }
        Map<String, String> values = new HashMap<>();
        stats.forEach((videoId, stat) -> {
            if (videoId != null && videoId > 0 && stat != null) {
                values.put(FeedCacheKeys.statKey(videoId), json.writeValueAsString(stat));
            }
        });
        writeMany(values, Duration.ofSeconds(RedisKeyConstant.TTL_VIDEO_STAT_SECONDS), "计数");
    }

    @Override
    public void evictStat(Long videoId) {
        if (videoId == null || videoId <= 0) {
            return;
        }
        try {
            redis.getBucket(FeedCacheKeys.statKey(videoId), StringCodec.INSTANCE).delete();
        } catch (RuntimeException ex) {
            log.warn("删除视频计数缓存失败 videoId={}: {}", videoId, ex.toString());
        }
    }

    private <T> Map<Long, T> readMany(List<Long> videoIds, java.util.function.LongFunction<String> keyOf,
                                      Class<T> type, String area) {
        Map<Long, T> result = new HashMap<>();
        if (videoIds == null || videoIds.isEmpty()) {
            return result;
        }
        List<Long> ids = videoIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (ids.isEmpty()) {
            return result;
        }
        try {
            String[] keys = ids.stream().map(keyOf::apply).toArray(String[]::new);
            RBuckets buckets = redis.getBuckets(StringCodec.INSTANCE);
            Map<String, String> values = buckets.get(keys);
            if (values == null) {
                return result;
            }
            for (Long id : ids) {
                String raw = values.get(keyOf.apply(id));
                if (raw == null) {
                    continue;
                }
                try {
                    result.put(id, json.readValue(raw, type));
                } catch (RuntimeException ex) {
                    log.warn("忽略无法解析的 Feed {}缓存 videoId={}: {}", area, id, ex.toString());
                }
            }
            return result;
        } catch (RuntimeException ex) {
            log.warn("读取 Feed {}缓存失败，改为查询数据库: {}", area, ex.toString());
            return Map.of();
        }
    }

    private void writeMany(Map<String, String> values, Duration ttl, String area) {
        if (values.isEmpty()) {
            return;
        }
        try {
            RBatch batch = redis.createBatch();
            values.forEach((key, value) -> batch.getBucket(key, StringCodec.INSTANCE).setAsync(value, ttl));
            batch.execute();
        } catch (RuntimeException ex) {
            log.warn("写入 Feed {}缓存失败: {}", area, ex.toString());
        }
    }
}
