package io.github.gmjneko.gcfeed.modules.feed.cache;

import io.github.gmjneko.gcfeed.common.constant.RedisKeyConstant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/** 页缓存键和确定性 TTL 抖动。同一个键每次算出的过期时间相同，不同键分散过期。 */
public final class FeedCacheKeys {

    private FeedCacheKeys() {
    }

    public static String pageKey(String scene, String cursor, int limit) {
        String normalized = cursor == null ? "" : cursor.strip();
        if (normalized.isEmpty()) {
            return RedisKeyConstant.FEED_PAGE_FIRST.formatted(scene, limit);
        }
        return RedisKeyConstant.FEED_PAGE_CURSOR.formatted(scene, limit, sha1(normalized));
    }

    public static Duration pageTtl(String cursor, String cacheKey) {
        long baseSeconds = cursor == null || cursor.isBlank()
                ? RedisKeyConstant.TTL_FEED_PAGE_FIRST_SECONDS
                : RedisKeyConstant.TTL_FEED_PAGE_CURSOR_SECONDS;
        int jitterPercent = 10 + Math.floorMod(cacheKey.hashCode(), 11);
        return Duration.ofMillis(baseSeconds * 1000L * (100 + jitterPercent) / 100);
    }

    public static String cardKey(long videoId) {
        return RedisKeyConstant.VIDEO_CARD.formatted(videoId);
    }

    public static String statKey(long videoId) {
        return RedisKeyConstant.VIDEO_STAT.formatted(videoId);
    }

    private static String sha1(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 is not available", ex);
        }
    }
}
