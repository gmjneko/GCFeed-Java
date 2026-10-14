package io.github.gmjneko.gcfeed.modules.mq;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Redis 记录消费回执。Redis 不可用时退回本机计数，至少让当前进程能停在最大重试次数。 */
@Component
public class RedisEventReceipts implements EventReceipts {

    private static final Logger log = LoggerFactory.getLogger(RedisEventReceipts.class);
    private static final Duration TTL = Duration.ofDays(7);

    private final RedissonClient redis;
    private final ConcurrentHashMap<String, AtomicInteger> localAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> localDone = new ConcurrentHashMap<>();

    public RedisEventReceipts(RedissonClient redis) {
        this.redis = redis;
    }

    @Override
    public boolean done(String consumer, String eventId) {
        String key = doneKey(consumer, eventId);
        if (localDone.containsKey(key)) {
            return true;
        }
        try {
            RBucket<String> bucket = redis.getBucket(key, StringCodec.INSTANCE);
            return bucket != null && bucket.isExists();
        } catch (RuntimeException ex) {
            log.warn("读取消费回执失败，按未完成处理 key={}: {}", key, ex.toString());
            return false;
        }
    }

    @Override
    public int nextAttempt(String consumer, String eventId) {
        String key = attemptKey(consumer, eventId);
        try {
            RAtomicLong counter = redis.getAtomicLong(key);
            long value = counter.incrementAndGet();
            counter.expire(TTL);
            return (int) Math.min(value, Integer.MAX_VALUE);
        } catch (RuntimeException ex) {
            log.warn("记录消费重试次数失败，改用本机计数 key={}: {}", key, ex.toString());
            return localAttempts.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
        }
    }

    @Override
    public void markDone(String consumer, String eventId) {
        String key = doneKey(consumer, eventId);
        localDone.put(key, Boolean.TRUE);
        try {
            RBucket<String> bucket = redis.getBucket(key, StringCodec.INSTANCE);
            if (bucket != null) {
                bucket.set("1", TTL);
            }
        } catch (RuntimeException ex) {
            log.warn("写入消费回执失败 key={}: {}", key, ex.toString());
        }
    }

    private static String doneKey(String consumer, String eventId) {
        return "mq:receipt:v1:" + consumer + ":" + eventId;
    }

    private static String attemptKey(String consumer, String eventId) {
        return "mq:attempt:v1:" + consumer + ":" + eventId;
    }
}
