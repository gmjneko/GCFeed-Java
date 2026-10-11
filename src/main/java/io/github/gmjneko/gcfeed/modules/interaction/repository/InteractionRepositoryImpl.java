package io.github.gmjneko.gcfeed.modules.interaction.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.gmjneko.gcfeed.common.constant.CommonConstant;
import io.github.gmjneko.gcfeed.modules.interaction.entity.InteractionAction;
import io.github.gmjneko.gcfeed.modules.interaction.mapper.InteractionActionMapper;
import io.github.gmjneko.gcfeed.modules.interaction.model.ActionType;
import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import io.github.gmjneko.gcfeed.modules.video.entity.VideoStat;
import io.github.gmjneko.gcfeed.modules.video.mapper.VideoMapper;
import io.github.gmjneko.gcfeed.modules.video.mapper.VideoStatMapper;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
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
    public Optional<Video> lockPublishedVideo(Long videoId) {
        // 固定 SQL 片段只控制行锁；视频状态修改/删除也需同一行的锁，从而与互动互斥。
        return Optional.ofNullable(videos.selectOne(Wrappers.<Video>lambdaQuery()
                .eq(Video::getId, videoId).eq(Video::getStatus, CommonConstant.VIDEO_STATUS_PUBLISHED)
                .last("FOR UPDATE")));
    }

    @Override
    public Optional<InteractionAction> lockAction(Long userId, Long videoId, ActionType type) {
        return Optional.ofNullable(actions.selectAnyStateForUpdate(userId, videoId, type.name()));
    }

    @Override
    public void insertAction(InteractionAction action) {
        actions.insert(action);
    }

    @Override
    public void setState(Long actionId, int status) {
        if (actions.restoreState(actionId, status, LocalDateTime.now()) != 1) {
            throw new IllegalStateException("interaction action disappeared");
        }
    }

    @Override
    public void changeCount(Long videoId, ActionType type, int delta) {
        // 由数据库原子增减；不能读出旧计数后在 Java 计算再覆盖并发写入。
        String expression = switch (type) {
            case LIKE -> "like_count = GREATEST(like_count + {0}, 0)";
            case FAVORITE -> "favorite_count = GREATEST(favorite_count + {0}, 0)";
        };
        int updated = stats.update(Wrappers.<VideoStat>lambdaUpdate().eq(VideoStat::getVideoId, videoId)
                .setSql(expression, delta).set(VideoStat::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) {
            throw new IllegalStateException("video stat disappeared");
        }
    }

    @Override
    public Optional<VideoStat> lockStat(Long videoId) {
        // 当前读既能获取最新提交的计数，也能在本事务提交前保持响应与此次修改一致。
        return Optional.ofNullable(stats.selectOne(Wrappers.<VideoStat>lambdaQuery()
                .eq(VideoStat::getVideoId, videoId).last("FOR UPDATE")));
    }
}
