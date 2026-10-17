package io.github.gmjneko.gcfeed.modules.video.service.impl;

import io.github.gmjneko.gcfeed.common.constant.RedisKeyConstant;
import io.github.gmjneko.gcfeed.modules.video.model.VideoStatField;
import io.github.gmjneko.gcfeed.modules.video.service.VideoStatDeltaBuffer;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 用 Redis Hash 保存每个视频的待刷库增量，用 SET 记录哪些视频有增量。
 *
 * <p>多实例的刷库任务通过 SPOP 各自取走不同的视频，不需要分布式锁；
 * 取走后、清空前新到的增量会重新把视频放回集合，下一轮再处理，不会丢失。
 */
@Component
public class RedisVideoStatDeltaBuffer implements VideoStatDeltaBuffer {

    private static final String ADD_SCRIPT = """
            redis.call('HINCRBY', KEYS[1], ARGV[1], ARGV[2])
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            redis.call('SADD', KEYS[2], ARGV[3])
            redis.call('EXPIRE', KEYS[2], ARGV[4])
            return 1
            """;

    private static final String DRAIN_SCRIPT = """
            local values = redis.call('HGETALL', KEYS[1])
            redis.call('DEL', KEYS[1])
            return values
            """;

    private final RedissonClient redisson;

    public RedisVideoStatDeltaBuffer(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Override
    public void add(Long videoId, VideoStatField field, long delta) {
        redisson.getScript(StringCodec.INSTANCE)
                .eval(
                        RScript.Mode.READ_WRITE,
                        ADD_SCRIPT,
                        RScript.ReturnType.LONG,
                        List.of(deltaKey(videoId), RedisKeyConstant.VIDEO_STAT_DIRTY),
                        field.column(),
                        String.valueOf(delta),
                        String.valueOf(videoId),
                        String.valueOf(RedisKeyConstant.TTL_VIDEO_STAT_DELTA_SECONDS)
                );
    }

    @Override
    public Map<VideoStatField, Long> pending(Long videoId) {
        Map<String, String> raw = redisson.<String, String>getMap(deltaKey(videoId), StringCodec.INSTANCE)
                .readAllMap();
        Map<VideoStatField, Long> result = new EnumMap<>(VideoStatField.class);
        raw.forEach((column, value) -> put(result, column, value));
        return result;
    }

    @Override
    public List<Long> pollDirty(int limit) {
        return redisson.<String>getSet(RedisKeyConstant.VIDEO_STAT_DIRTY, StringCodec.INSTANCE)
                .removeRandom(limit).stream()
                .map(Long::valueOf)
                .toList();
    }

    @Override
    public Map<VideoStatField, Long> drain(Long videoId) {
        List<Object> flat = redisson.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE, DRAIN_SCRIPT,
                RScript.ReturnType.LIST, List.of(deltaKey(videoId)));
        Map<VideoStatField, Long> result = new EnumMap<>(VideoStatField.class);
        for (int i = 0; i + 1 < flat.size(); i += 2) {
            put(result, String.valueOf(flat.get(i)), String.valueOf(flat.get(i + 1)));
        }
        return result;
    }

    private void put(Map<VideoStatField, Long> result, String column, String value) {
        VideoStatField field = VideoStatField.fromColumn(column);
        long delta = Long.parseLong(value);
        if (field != null && delta != 0) {
            result.put(field, delta);
        }
    }

    private String deltaKey(Long videoId) {
        return RedisKeyConstant.VIDEO_STAT_DELTA.formatted(videoId);
    }
}
