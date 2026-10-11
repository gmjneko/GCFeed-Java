package io.github.gmjneko.gcfeed.modules.video.repository;

import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import io.github.gmjneko.gcfeed.modules.video.entity.VideoStat;
import io.github.gmjneko.gcfeed.modules.video.vo.VideoCursor;

import java.util.List;
import java.util.Optional;

/**
 * 视频持久化边界。
 *
 * <p>Repository 将视频主表和统计表的存取细节封装起来，Service 不直接依赖 MyBatis-Plus。
 */
public interface VideoRepository {

    Optional<Video> findPublishedById(Long videoId);

    Optional<Video> findAnyStatusById(Long videoId);

    Optional<Video> findByAuthorAndIdempotencyKey(Long authorId, String idempotencyKey);

    List<Video> findPublicByAuthor(Long authorId, int limit, VideoCursor cursor);

    List<Video> findMineByAuthor(Long authorId, int limit, VideoCursor cursor);

    int insertVideo(Video video);

    int insertStat(VideoStat stat);

    int markDeleted(Long videoId);
}
