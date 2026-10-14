package io.github.gmjneko.gcfeed.modules.playback.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.constant.CommonConstant;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.modules.playback.entity.PlaybackConfig;
import io.github.gmjneko.gcfeed.modules.playback.entity.PlaybackQosLog;
import io.github.gmjneko.gcfeed.modules.playback.mapper.PlaybackConfigMapper;
import io.github.gmjneko.gcfeed.modules.playback.mapper.PlaybackQosLogMapper;
import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import io.github.gmjneko.gcfeed.modules.video.mapper.VideoMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PlaybackRepository {

    private final PlaybackConfigMapper configs;
    private final PlaybackQosLogMapper reports;
    private final VideoMapper videos;

    public PlaybackRepository(PlaybackConfigMapper configs, PlaybackQosLogMapper reports, VideoMapper videos) {
        this.configs = configs;
        this.reports = reports;
        this.videos = videos;
    }

    public Optional<PlaybackConfig> findConfig(String platform, String networkType) {
        return Optional.ofNullable(configs.selectOne(Wrappers.<PlaybackConfig>lambdaQuery()
                .eq(PlaybackConfig::getPlatform, platform)
                .eq(PlaybackConfig::getNetworkType, networkType)));
    }

    /** 当前视频必须已上线且有发布时间，否则调用方退回最新视频。 */
    public Optional<Video> findPublishedAnchor(Long videoId) {
        return Optional.ofNullable(videos.selectOne(Wrappers.<Video>lambdaQuery()
                .select(Video::getId, Video::getPublishedAt)
                .eq(Video::getId, videoId)
                .eq(Video::getStatus, CommonConstant.VIDEO_STATUS_PUBLISHED)
                .isNotNull(Video::getPublishedAt)));
    }

    public List<Video> listPreload(LocalDateTime publishedAt, Long videoId, int limit) {
        var query = Wrappers.<Video>lambdaQuery()
                .select(Video::getId, Video::getMediaUrl, Video::getCoverUrl, Video::getPublishedAt)
                .eq(Video::getStatus, CommonConstant.VIDEO_STATUS_PUBLISHED)
                .isNotNull(Video::getPublishedAt);
        if (publishedAt != null && videoId != null) {
            query.and(nested -> nested.lt(Video::getPublishedAt, publishedAt)
                    .or(sameTime -> sameTime.eq(Video::getPublishedAt, publishedAt)
                            .lt(Video::getId, videoId)));
        }
        query.orderByDesc(Video::getPublishedAt, Video::getId);
        return videos.selectPage(new Page<>(1, limit, false), query).getRecords();
    }

    /** 幂等键冲突时返回第一次上报，不覆盖指标。 */
    public InsertResult insertReport(PlaybackQosLog report) {
        try {
            reports.insert(report);
            return new InsertResult(report, true);
        } catch (DuplicateKeyException ex) {
            if (report.getIdempotencyKey() == null) {
                throw new BusinessException(ErrorCode.SAVE_QOS_FAILED, ex);
            }
            PlaybackQosLog existing = reports.selectOne(Wrappers.<PlaybackQosLog>lambdaQuery()
                    .eq(PlaybackQosLog::getUserId, report.getUserId())
                    .eq(PlaybackQosLog::getIdempotencyKey, report.getIdempotencyKey())
                    .orderByDesc(PlaybackQosLog::getId)
                    .last("LIMIT 1"));
            if (existing == null) {
                throw new BusinessException(ErrorCode.SAVE_QOS_FAILED, ex);
            }
            return new InsertResult(existing, false);
        }
    }

    public record InsertResult(PlaybackQosLog report, boolean created) {
    }
}
