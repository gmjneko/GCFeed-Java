package io.github.gmjneko.gcfeed.modules.message.cache;

import io.github.gmjneko.gcfeed.common.constant.RedisKeyConstant;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.OptionalInt;

/** 未读数的短缓存。读写异常交给调用方回源，不在这里吞掉。 */
@Component
public class MessageUnreadCache {

    private final RedissonClient redis;

    public MessageUnreadCache(RedissonClient redis) {
        this.redis = redis;
    }

    public OptionalInt get(Long userId) {
        String value = redis.<String>getBucket(key(userId), StringCodec.INSTANCE).get();
        if (value == null || value.isBlank()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.parseInt(value));
    }

    public void put(Long userId, int count) {
        redis.<String>getBucket(key(userId), StringCodec.INSTANCE)
                .set(Integer.toString(count), Duration.ofSeconds(RedisKeyConstant.TTL_MESSAGE_UNREAD_SECONDS));
    }

    public void evict(Long userId) {
        redis.getBucket(key(userId), StringCodec.INSTANCE).delete();
    }

    static String key(Long userId) {
        return RedisKeyConstant.MESSAGE_UNREAD.formatted(userId);
    }
}
