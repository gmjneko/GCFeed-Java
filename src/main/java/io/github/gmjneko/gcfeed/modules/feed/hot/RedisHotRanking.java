package io.github.gmjneko.gcfeed.modules.feed.hot;

import io.github.gmjneko.gcfeed.common.constant.RedisKeyConstant;
import io.github.gmjneko.gcfeed.modules.feed.support.SingleFlight;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.ScoredEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 用 Redis 分钟桶保存近一小时热度。窗口在第一次读取时合并，并在翻页期间保持不变。 */
@Component
public class RedisHotRanking implements HotRanking {

    private static final Logger log = LoggerFactory.getLogger(RedisHotRanking.class);

    private final RedissonClient redis;
    private final Clock clock;
    private final SingleFlight<Boolean> rebuilds = new SingleFlight<>();

    public RedisHotRanking(RedissonClient redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public void add(long videoId, int scoreDelta) {
        if (videoId <= 0 || scoreDelta == 0) {
            return;
        }
        try {
            String key = HotWindows.minuteKey(HotWindows.minute(clock.instant()));
            RScoredSortedSet<String> bucket = buckets(key);
            bucket.addScore(HotWindows.member(videoId), scoreDelta);
            bucket.expire(Duration.ofSeconds(RedisKeyConstant.TTL_FEED_HOT_MINUTE_SECONDS));
        } catch (RuntimeException ex) {
            log.warn("写入热榜分钟桶失败，互动结果保持有效 videoId={} delta={}: {}", videoId, scoreDelta, ex.toString());
        }
    }

    @Override
    public boolean available() {
        try {
            buckets(RedisKeyConstant.FEED_HOT_WINDOW.formatted(0)).isExists();
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public HotPage page(LocalDateTime windowEnd, int offset, int limit) {
        if (limit <= 0) {
            return HotPage.ready(List.of());
        }
        int start = Math.max(offset, 0);
        try {
            LocalDateTime end = windowEnd.truncatedTo(ChronoUnit.MINUTES);
            RScoredSortedSet<String> window = buckets(HotWindows.windowKey(end));
            if (!window.isExists()) {
                rebuilds.call(HotWindows.windowKey(end), () -> {
                    if (!window.isExists()) {
                        rebuild(window, end);
                    }
                    return Boolean.TRUE;
                });
            }
            Collection<ScoredEntry<String>> entries = window.entryRangeReversed(start, start + limit - 1);
            List<Long> ids = new ArrayList<>(entries.size());
            for (ScoredEntry<String> entry : entries) {
                Long videoId = HotWindows.videoId(entry.getValue());
                if (videoId != null) {
                    ids.add(videoId);
                }
            }
            return HotPage.ready(ids);
        } catch (RuntimeException ex) {
            return HotPage.unavailable(ex);
        }
    }

    private void rebuild(RScoredSortedSet<String> window, LocalDateTime windowEnd) {
        String[] minutes = HotWindows.minutes(windowEnd).stream().map(HotWindows::minuteKey).toArray(String[]::new);
        window.union(RScoredSortedSet.Aggregate.SUM, minutes);
        window.removeRangeByScore(Double.NEGATIVE_INFINITY, true, 0, true);
        window.expire(Duration.ofSeconds(RedisKeyConstant.TTL_FEED_HOT_WINDOW_SECONDS));
    }

    private RScoredSortedSet<String> buckets(String key) {
        return redis.getScoredSortedSet(key, StringCodec.INSTANCE);
    }
}
