package io.github.gmjneko.gcfeed.modules.video.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.gmjneko.gcfeed.common.constant.CommonConstant;
import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import io.github.gmjneko.gcfeed.modules.video.entity.VideoStat;
import io.github.gmjneko.gcfeed.modules.video.mapper.VideoMapper;
import io.github.gmjneko.gcfeed.modules.video.mapper.VideoStatMapper;
import io.github.gmjneko.gcfeed.modules.video.vo.VideoCursor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** MyBatis-Plus 视频持久化实现，不使用 XML Mapper。 */
@Repository
public class VideoRepositoryImpl implements VideoRepository {

    private final VideoMapper videoMapper;
    private final VideoStatMapper videoStatMapper;

    public VideoRepositoryImpl(VideoMapper videoMapper, VideoStatMapper videoStatMapper) {
        this.videoMapper = videoMapper;
        this.videoStatMapper = videoStatMapper;
    }

    @Override
    public Optional<Video> findPublishedById(Long videoId) {
        Video video = videoMapper.selectOne(Wrappers.<Video>lambdaQuery()
                .eq(Video::getId, videoId)
                .eq(Video::getStatus, CommonConstant.VIDEO_STATUS_PUBLISHED));
        return withStat(video);
    }

    @Override
    public Optional<Video> findAnyStatusById(Long videoId) {
        return withStat(videoMapper.selectAnyStatusById(videoId));
    }

    @Override
    public Optional<Video> findByAuthorAndIdempotencyKey(Long authorId, String idempotencyKey) {
        return withStat(videoMapper.selectByLegacyKey(authorId, idempotencyKey));
    }

    @Override
    public List<Video> findPublicByAuthor(Long authorId, int limit, VideoCursor cursor) {
        LambdaQueryWrapper<Video> query = baseAuthorQuery(authorId)
                .eq(Video::getStatus, CommonConstant.VIDEO_STATUS_PUBLISHED);
        applyCursor(query, cursor);
        return page(query, limit);
    }

    @Override
    public List<Video> findMineByAuthor(Long authorId, int limit, VideoCursor cursor) {
        LambdaQueryWrapper<Video> query = baseAuthorQuery(authorId)
                .in(Video::getStatus, CommonConstant.VIDEO_STATUS_PUBLISHED,
                        CommonConstant.VIDEO_STATUS_OFFLINE);
        applyCursor(query, cursor);
        return page(query, limit);
    }

    private LambdaQueryWrapper<Video> baseAuthorQuery(Long authorId) {
        return Wrappers.<Video>lambdaQuery()
                .eq(Video::getAuthorId, authorId)
                .eq(Video::getDeleted, 0)
                .orderByDesc(Video::getPublishedAt)
                .orderByDesc(Video::getId);
    }

    /** 双键游标条件与排序严格对应，避免相同发布时间造成重复或遗漏。 */
    private void applyCursor(LambdaQueryWrapper<Video> query, VideoCursor cursor) {
        if (cursor == null) {
            return;
        }
        query.and(condition -> condition
                .lt(Video::getPublishedAt, cursor.publishedAt())
                .or(equalTime -> equalTime
                        .eq(Video::getPublishedAt, cursor.publishedAt())
                        .lt(Video::getId, cursor.videoId())));
    }

    /** 使用 MyBatis-Plus 分页插件，关闭 count，只取 limit 条记录。 */
    private List<Video> page(LambdaQueryWrapper<Video> query, int limit) {
        Page<Video> page = new Page<>(1, limit, false);
        return withStats(videoMapper.selectPage(page, query).getRecords());
    }

    @Override
    public int insertVideo(Video video) {
        return videoMapper.insert(video);
    }

    @Override
    public int insertStat(VideoStat stat) {
        return videoStatMapper.insert(stat);
    }

    @Override
    public int markDeleted(Long videoId) {
        LambdaUpdateWrapper<Video> update = Wrappers.lambdaUpdate();
        update.eq(Video::getId, videoId)
                .eq(Video::getDeleted, 0)
                .set(Video::getStatus, CommonConstant.VIDEO_STATUS_DELETED)
                .set(Video::getDeleted, 1);
        return videoMapper.update(null, update);
    }

    private Optional<Video> withStat(Video video) {
        if (video == null) {
            return Optional.empty();
        }
        VideoStat stat = videoStatMapper.selectById(video.getId());
        applyStat(video, stat);
        return Optional.of(video);
    }

    private List<Video> withStats(List<Video> videos) {
        if (videos.isEmpty()) {
            return videos;
        }
        List<Long> ids = videos.stream().map(Video::getId).toList();
        var stats = videoStatMapper.selectBatchIds(ids).stream()
                .collect(java.util.stream.Collectors.toMap(VideoStat::getVideoId, stat -> stat));
        videos.forEach(video -> applyStat(video, stats.get(video.getId())));
        return videos;
    }

    private void applyStat(Video video, VideoStat stat) {
        video.setLikeCount(stat == null ? 0 : valueOrZero(stat.getLikeCount()));
        video.setCommentCount(stat == null ? 0 : valueOrZero(stat.getCommentCount()));
        video.setFavoriteCount(stat == null ? 0 : valueOrZero(stat.getFavoriteCount()));
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

}
