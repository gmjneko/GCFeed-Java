package io.github.gmjneko.gcfeed.modules.video.repository;

import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import io.github.gmjneko.gcfeed.modules.video.entity.VideoStat;
import io.github.gmjneko.gcfeed.modules.video.model.VideoStatField;
import io.github.gmjneko.gcfeed.modules.video.vo.VideoCursor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 视频持久化边界。
 *
 * <p>Repository 将视频主表和统计表的存取细节封装起来，Service 不直接依赖 MyBatis-Plus。
 */
public interface VideoRepository {

    /** 只判断视频是否上线，不读取视频详情与互动统计。 */
    boolean existsPublished(Long videoId);

    /** 批量筛出已上线的视频 ID，供曝光整批资格校验使用；空列表不查库。 */
    java.util.Set<Long> findPublishedIds(List<Long> videoIds);

    /** 查询已上线的视频及互动统计，不存在或未上线时返回 empty。 */
    Optional<Video> findPublishedById(Long videoId);

    /** 查询任意状态的视频及互动统计，供需要自行判断视频状态的业务使用。 */
    Optional<Video> findAnyStatusById(Long videoId);

    /** 查询视频主表并加行锁，供事务内需要串行处理视频状态的写操作使用。 */
    Optional<Video> lockById(Long videoId);

    /** 按作者和幂等键查找已创建的视频及统计，供重复发布请求复用结果。 */
    Optional<Video> findByAuthorAndIdempotencyKey(Long authorId, String idempotencyKey);

    /** 按时间游标读取作者已上线的公开视频及统计，最多返回 limit 条。 */
    List<Video> findPublicByAuthor(Long authorId, int limit, VideoCursor cursor);

    /** 按时间游标读取作者本人可见的上线、下线视频及统计，最多返回 limit 条。 */
    List<Video> findMineByAuthor(Long authorId, int limit, VideoCursor cursor);

    /** 插入视频主表并回填自增 ID，返回影响行数。 */
    int insertVideo(Video video);

    /** 为视频插入互动统计记录，返回影响行数。 */
    int insertStat(VideoStat stat);

    /** 将尚未删除的视频标记为删除态，返回影响行数。 */
    int markDeleted(Long videoId);

    /** 原子累加计数增量，GREATEST 兜底防止减成负数；返回 0 表示统计行不存在。 */
    int addStatDeltas(Long videoId, Map<VideoStatField, Long> deltas);
}
