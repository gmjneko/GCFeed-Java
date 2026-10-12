package io.github.gmjneko.gcfeed.modules.interaction.job;

import io.github.gmjneko.gcfeed.modules.interaction.model.ActionType;
import io.github.gmjneko.gcfeed.modules.interaction.repository.InteractionRepository;
import io.github.gmjneko.gcfeed.modules.video.entity.VideoStat;
import io.github.gmjneko.gcfeed.modules.video.model.VideoStatField;
import io.github.gmjneko.gcfeed.modules.video.service.VideoStatDeltaBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 按 interaction_action 中的有效行为重算点赞数与收藏数，纠正缓冲丢失、提交后记录失败等造成的偏差。
 *
 * <p>目标值扣除尚未刷库的增量，刷库后恰好等于事实数量。与正在进行的点赞或刷库并发时
 * 可能留下 ±1 的短暂偏差，下一轮对账收敛。
 */
@Component
public class InteractionStatReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(InteractionStatReconcileJob.class);

    private final InteractionRepository repository;
    private final VideoStatDeltaBuffer buffer;
    private final int batchSize;

    public InteractionStatReconcileJob(InteractionRepository repository, VideoStatDeltaBuffer buffer,
                                       @Value("${gcfeed.stat.reconcile-batch-size:200}") int batchSize) {
        this.repository = repository;
        this.buffer = buffer;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${gcfeed.stat.reconcile-interval-ms:1800000}",
            initialDelayString = "${gcfeed.stat.reconcile-initial-delay-ms:60000}")
    public void reconcile() {
        long afterVideoId = 0;
        List<VideoStat> batch;
        do {
            batch = repository.findStatsAfter(afterVideoId, batchSize);
            if (batch.isEmpty()) {
                return;
            }
            Map<Long, Map<ActionType, Long>> active = repository.countActive(
                    batch.stream().map(VideoStat::getVideoId).toList());
            for (VideoStat stat : batch) {
                reconcileOne(stat, active.getOrDefault(stat.getVideoId(), Map.of()));
            }
            afterVideoId = batch.getLast().getVideoId();
        } while (batch.size() == batchSize);
    }

    private void reconcileOne(VideoStat stat, Map<ActionType, Long> active) {
        Map<VideoStatField, Long> pending = buffer.pending(stat.getVideoId());
        long likes = target(active, pending, ActionType.LIKE);
        long favorites = target(active, pending, ActionType.FAVORITE);
        if (valueOrZero(stat.getLikeCount()) != likes || valueOrZero(stat.getFavoriteCount()) != favorites) {
            repository.overwriteCounts(stat.getVideoId(), likes, favorites);
            log.info("对账修正视频计数 videoId={} like {}->{} favorite {}->{}", stat.getVideoId(),
                    stat.getLikeCount(), likes, stat.getFavoriteCount(), favorites);
        }
    }

    private long target(Map<ActionType, Long> active, Map<VideoStatField, Long> pending, ActionType type) {
        return Math.max(0, active.getOrDefault(type, 0L) - pending.getOrDefault(type.statField(), 0L));
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
