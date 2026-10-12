package io.github.gmjneko.gcfeed.modules.video;

import io.github.gmjneko.gcfeed.modules.video.model.VideoStatField;
import io.github.gmjneko.gcfeed.modules.video.service.VideoStatDeltaBuffer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 与 Redis 实现语义一致的内存缓冲，测试中替代 Redis 并由用例手动触发刷库。 */
public class InMemoryVideoStatDeltaBuffer implements VideoStatDeltaBuffer {

    private final Map<Long, Map<VideoStatField, Long>> deltas = new HashMap<>();
    private final Set<Long> dirty = new LinkedHashSet<>();

    @Override
    public synchronized void add(Long videoId, VideoStatField field, long delta) {
        deltas.computeIfAbsent(videoId, ignored -> new EnumMap<>(VideoStatField.class)).merge(field, delta, Long::sum);
        dirty.add(videoId);
    }

    @Override
    public synchronized Map<VideoStatField, Long> pending(Long videoId) {
        Map<VideoStatField, Long> result = new EnumMap<>(VideoStatField.class);
        deltas.getOrDefault(videoId, Map.of()).forEach((field, delta) -> {
            if (delta != 0) {
                result.put(field, delta);
            }
        });
        return result;
    }

    @Override
    public synchronized List<Long> pollDirty(int limit) {
        List<Long> polled = new ArrayList<>();
        var iterator = dirty.iterator();
        while (iterator.hasNext() && polled.size() < limit) {
            polled.add(iterator.next());
            iterator.remove();
        }
        return polled;
    }

    @Override
    public synchronized Map<VideoStatField, Long> drain(Long videoId) {
        Map<VideoStatField, Long> result = pending(videoId);
        deltas.remove(videoId);
        return result;
    }

    public synchronized void clear() {
        deltas.clear();
        dirty.clear();
    }
}
