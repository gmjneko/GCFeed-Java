package io.github.gmjneko.gcfeed.modules.recommendation.mapper;

import io.github.gmjneko.gcfeed.modules.recommendation.model.RecommendationCandidate;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** JOIN 去重和计算热度需要手写 SQL；单表曝光与向量读取仍归各自 Repository。 */
public interface RecommendationMapper {

    /** 联查视频、统计和曝光表，按热度顺序召回该用户近期未曝光的上线候选。 */
    // 曝光时间条件放在 JOIN 中，过期的聚合行不阻止召回；SIGNED 将 INT 计数按长整数计算。
    @Select("""
            SELECT v.id AS video_id, v.author_id,
                   COALESCE(s.like_count, 0) AS like_count,
                   COALESCE(s.comment_count, 0) AS comment_count,
                   COALESCE(s.favorite_count, 0) AS favorite_count, v.published_at
            FROM video v LEFT JOIN video_stat s ON s.video_id = v.id
            LEFT JOIN exposures e ON e.user_id = #{userId} AND e.video_id = v.id
                                 AND e.last_exposed_at >= #{windowStart}
            WHERE v.status = 2 AND v.published_at IS NOT NULL AND e.video_id IS NULL
            ORDER BY (CAST(COALESCE(s.like_count, 0) AS SIGNED) * 3
                    + CAST(COALESCE(s.comment_count, 0) AS SIGNED) * 5
                    + CAST(COALESCE(s.favorite_count, 0) AS SIGNED) * 4) DESC,
                     v.published_at DESC, v.id DESC
            LIMIT #{limit}
            """)
    List<RecommendationCandidate> recall(@Param("userId") Long userId,
                                         @Param("windowStart") LocalDateTime windowStart,
                                         @Param("limit") int limit);

    /** 批量检查快照中的视频是否仍上线且近期未曝光，返回可继续展示的 ID。 */
    // 翻页只查询当前资格，不重读热度或重算排名；空 ID 集合由 Repository 提前短路。
    @Select("""
            <script>
            SELECT v.id FROM video v
            LEFT JOIN exposures e ON e.user_id = #{userId} AND e.video_id = v.id
                                 AND e.last_exposed_at >= #{windowStart}
            WHERE v.status = 2 AND v.published_at IS NOT NULL AND e.video_id IS NULL AND v.id IN
            <foreach collection="videoIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Long> findEligibleIds(@Param("userId") Long userId, @Param("videoIds") List<Long> videoIds,
                              @Param("windowStart") LocalDateTime windowStart);

}
