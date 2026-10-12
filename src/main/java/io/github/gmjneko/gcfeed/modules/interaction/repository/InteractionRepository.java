package io.github.gmjneko.gcfeed.modules.interaction.repository;

import io.github.gmjneko.gcfeed.modules.interaction.model.ActionType;
import io.github.gmjneko.gcfeed.modules.video.entity.VideoStat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 互动持久化边界。
 *
 * <p>状态变更不加显式行锁：条件 UPDATE 的影响行数和唯一键冲突共同决定状态是否真的变化，
 * 调用方需在读已提交隔离级别下使用，避免可重复读的间隙锁让并发首次插入互相死锁。
 */
public interface InteractionRepository {

    /** 普通读判断视频是否已发布；与删除并发时最多让已删除视频多一次计数，不影响展示。 */
    boolean isPublishedVideo(Long videoId);

    /** 把取消态翻成有效态，行不存在时插入；返回状态是否真的从未生效变为生效。 */
    boolean activate(Long userId, Long videoId, ActionType type);

    /** 把有效态翻成取消态；返回状态是否真的从生效变为未生效。没有记录时不建行。 */
    boolean cancel(Long userId, Long videoId, ActionType type);

    /** 读取已刷库的视频统计行。 */
    Optional<VideoStat> findStat(Long videoId);

    /** 按 video_id 升序读取一批统计行，供对账游标遍历。 */
    List<VideoStat> findStatsAfter(Long afterVideoId, int limit);

    /** 按视频统计各类有效行为的数量，结果只包含数量大于 0 的组合。 */
    Map<Long, Map<ActionType, Long>> countActive(List<Long> videoIds);

    /** 用对账结果覆盖点赞数与收藏数。 */
    void overwriteCounts(Long videoId, long likeCount, long favoriteCount);
}
