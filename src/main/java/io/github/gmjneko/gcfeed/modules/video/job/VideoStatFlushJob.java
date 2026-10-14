package io.github.gmjneko.gcfeed.modules.video.job;

import io.github.gmjneko.gcfeed.modules.feed.cache.FeedCache;
import io.github.gmjneko.gcfeed.modules.video.model.VideoStatField;
import io.github.gmjneko.gcfeed.modules.video.repository.VideoRepository;
import io.github.gmjneko.gcfeed.modules.video.service.VideoStatDeltaBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 定时把缓冲中的计数增量批量写入 video_stat。
 *
 * <p>每个视频一次 UPDATE，热门视频在一个周期内的成百上千次点赞合并成一次写入，
 * 计数行不再随请求串行加锁。
 */
@Component
public class VideoStatFlushJob {

    private static final Logger log = LoggerFactory.getLogger(VideoStatFlushJob.class);

    private final VideoStatDeltaBuffer buffer;
    private final VideoRepository videoRepository;
    private final FeedCache feedCache;
    private final int batchSize;

    public VideoStatFlushJob(VideoStatDeltaBuffer buffer, VideoRepository videoRepository, FeedCache feedCache,
                             @Value("${gcfeed.stat.flush-batch-size:500}") int batchSize) {
        this.buffer = buffer;
        this.videoRepository = videoRepository;
        this.feedCache = feedCache;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${gcfeed.stat.flush-interval-ms:2000}")
    public void flush() {
        while (true) {
            List<Long> videoIds = buffer.pollDirty(batchSize);
            boolean allFlushed = true;
            for (Long videoId : videoIds) {
                allFlushed &= flushOne(videoId);
            }
            // 失败的增量已放回缓冲，本轮不再继续取，避免数据库故障时原地空转。
            if (videoIds.size() < batchSize || !allFlushed) {
                return;
            }
        }
    }

    private boolean flushOne(Long videoId) {
        Map<VideoStatField, Long> deltas = buffer.drain(videoId);
        if (deltas.isEmpty()) {
            return true;
        }
        try {
            if (videoRepository.addStatDeltas(videoId, deltas) == 0) {
                log.warn("视频统计行不存在，丢弃计数增量 videoId={} deltas={}", videoId, deltas);
            }
            // 库里的基数已经变了，必须丢掉旧缓存，否则待刷增量被清空后读数会退回刷库前的值。
            feedCache.evictStat(videoId);
            return true;
        } catch (RuntimeException ex) {
            restore(videoId, deltas, ex);
            return false;
        }
    }

    /** 写库失败时把增量放回缓冲，下一轮重试；放回也失败时只能等对账任务按事实表纠正。 */
    private void restore(Long videoId, Map<VideoStatField, Long> deltas, RuntimeException cause) {
        try {
            deltas.forEach((field, delta) -> buffer.add(videoId, field, delta));
            log.warn("计数增量刷库失败，已放回缓冲 videoId={} deltas={}", videoId, deltas, cause);
        } catch (RuntimeException restoreError) {
            cause.addSuppressed(restoreError);
            log.error("计数增量刷库失败且无法放回缓冲，等待对账纠正 videoId={} deltas={}", videoId, deltas, cause);
        }
    }
}
