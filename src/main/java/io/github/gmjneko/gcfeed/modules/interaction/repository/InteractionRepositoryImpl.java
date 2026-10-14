package io.github.gmjneko.gcfeed.modules.interaction.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.gmjneko.gcfeed.common.constant.CommonConstant;
import io.github.gmjneko.gcfeed.modules.interaction.entity.InteractionAction;
import io.github.gmjneko.gcfeed.modules.interaction.mapper.InteractionActionMapper;
import io.github.gmjneko.gcfeed.modules.interaction.model.ActionType;
import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import io.github.gmjneko.gcfeed.modules.video.entity.VideoStat;
import io.github.gmjneko.gcfeed.modules.video.mapper.VideoMapper;
import io.github.gmjneko.gcfeed.modules.video.mapper.VideoStatMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class InteractionRepositoryImpl implements InteractionRepository {
    private final VideoMapper videos;
    private final InteractionActionMapper actions;
    private final VideoStatMapper stats;

    public InteractionRepositoryImpl(VideoMapper videos, InteractionActionMapper actions, VideoStatMapper stats) {
        this.videos = videos;
        this.actions = actions;
        this.stats = stats;
    }

    @Override
    public boolean isPublishedVideo(Long videoId) {
        return videos.exists(Wrappers.<Video>lambdaQuery()
                .eq(Video::getId, videoId)
                .eq(Video::getStatus, CommonConstant.VIDEO_STATUS_PUBLISHED));
    }

    @Override
    public Optional<Long> publishedAuthorId(Long videoId) {
        Video video = videos.selectOne(Wrappers.<Video>lambdaQuery()
                .select(Video::getAuthorId)
                .eq(Video::getId, videoId)
                .eq(Video::getStatus, CommonConstant.VIDEO_STATUS_PUBLISHED));
        return video == null || video.getAuthorId() == null ? Optional.empty() : Optional.of(video.getAuthorId());
    }

    @Override
    public boolean activate(Long userId, Long videoId, ActionType type) {
        if (flip(userId, videoId, type, InteractionAction.STATUS_CANCELED, InteractionAction.STATUS_ACTIVE)) {
            return true;
        }
        InteractionAction action = new InteractionAction();
        action.setUserId(userId);
        action.setVideoId(videoId);
        action.setActionType(type.name());
        action.setStatus(InteractionAction.STATUS_ACTIVE);
        try {
            actions.insert(action);
            return true;
        } catch (DuplicateKeyException ex) {
            // 行已存在且不是取消态，即本来就有效，或并发请求刚刚插入成功。
            return false;
        }
    }

    @Override
    public boolean cancel(Long userId, Long videoId, ActionType type) {
        return flip(userId, videoId, type, InteractionAction.STATUS_ACTIVE, InteractionAction.STATUS_CANCELED);
    }

    /** WHERE 带上原状态，并发请求中只有一个能完成同一次翻转。 */
    private boolean flip(Long userId, Long videoId, ActionType type, int from, int to) {
        return actions.update(Wrappers.<InteractionAction>lambdaUpdate()
                .eq(InteractionAction::getUserId, userId)
                .eq(InteractionAction::getVideoId, videoId)
                .eq(InteractionAction::getActionType, type.name())
                .eq(InteractionAction::getStatus, from)
                .set(InteractionAction::getStatus, to)
                .set(InteractionAction::getUpdatedAt, LocalDateTime.now())) == 1;
    }

    @Override
    public Optional<VideoStat> findStat(Long videoId) {
        return Optional.ofNullable(stats.selectById(videoId));
    }

    @Override
    public List<VideoStat> findStatsAfter(Long afterVideoId, int limit) {
        return stats.selectPage(new Page<>(1, limit, false), Wrappers.<VideoStat>lambdaQuery()
                .select(VideoStat::getVideoId, VideoStat::getLikeCount, VideoStat::getFavoriteCount)
                .gt(VideoStat::getVideoId, afterVideoId)
                .orderByAsc(VideoStat::getVideoId)).getRecords();
    }

    @Override
    public Map<Long, Map<ActionType, Long>> countActive(List<Long> videoIds) {
        if (videoIds.isEmpty()) {
            return Map.of();
        }
        // 走 idx_video_type_status 覆盖索引，按视频和行为类型分组计数。
        List<Map<String, Object>> rows = actions.selectMaps(Wrappers.<InteractionAction>query()
                .select("video_id", "action_type", "COUNT(*) AS total")
                .in("video_id", videoIds)
                .eq("status", InteractionAction.STATUS_ACTIVE)
                .groupBy("video_id", "action_type"));
        Map<Long, Map<ActionType, Long>> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            ActionType type = parseType(String.valueOf(row.get("action_type")));
            if (type != null) {
                result.computeIfAbsent(((Number) row.get("video_id")).longValue(),
                        ignored -> new EnumMap<>(ActionType.class))
                        .put(type, ((Number) row.get("total")).longValue());
            }
        }
        return result;
    }

    @Override
    public void overwriteCounts(Long videoId, long likeCount, long favoriteCount) {
        stats.update(Wrappers.<VideoStat>lambdaUpdate()
                .eq(VideoStat::getVideoId, videoId)
                .set(VideoStat::getLikeCount, likeCount)
                .set(VideoStat::getFavoriteCount, favoriteCount)
                .set(VideoStat::getUpdatedAt, LocalDateTime.now()));
    }

    private ActionType parseType(String value) {
        for (ActionType type : ActionType.values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return null;
    }
}
