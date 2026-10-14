package io.github.gmjneko.gcfeed.modules.video.mapper;

import io.github.gmjneko.gcfeed.modules.feed.model.FeedCursor;
import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface VideoMapper extends BaseMapper<Video> {

    /**
     * 按游标查询当前用户关注流的一页。
     *
     * <p>手写理由：相关子查询 {@code EXISTS}（见规范 §4.3 判据二）。改用「先取关注列表再拼
     * {@code IN}」不是等价改写——关注数很大时会把整份 ID 列表塞进 SQL，且每翻一页都要先全量
     * 拿到 IDs 才能筛选，无法像 {@code EXISTS} 这样走 {@code idx_status_published} 取 limit 条就停。
     */
    @Select("""
            <script>
            SELECT v.id, v.author_id, v.title, v.description, v.media_url, v.cover_url, v.published_at
            FROM video v
            WHERE v.status = 2 AND v.published_at IS NOT NULL
              AND EXISTS (SELECT 1 FROM user_follow f WHERE f.user_id = #{viewerId}
                          AND f.target_user_id = v.author_id AND f.status = 1)
            <if test="cursor != null">
              AND (v.published_at &lt; #{cursor.publishedAt}
                   OR (v.published_at = #{cursor.publishedAt} AND v.id &lt; #{cursor.videoId}))
            </if>
            ORDER BY v.published_at DESC, v.id DESC LIMIT #{fetchLimit}
            </script>
            """)
    java.util.List<Video> followingPage(@Param("viewerId") Long viewerId,
                                        @Param("cursor") FeedCursor cursor,
                                        @Param("fetchLimit") int fetchLimit);

    /**
     * 热榜降级：按累计热度、发布时间、视频 ID 倒序取一页。
     *
     * <p>手写理由：排序键是点赞、评论、收藏的加权表达式，不是单表列，Lambda 无法同时表达
     * 计算列、三键游标和 LEFT JOIN。
     */
    @Select("""
            <script>
            SELECT id, author_id, title, description, media_url, cover_url, published_at, hot_score
            FROM (
                SELECT v.id, v.author_id, v.title, v.description, v.media_url, v.cover_url, v.published_at,
                       (COALESCE(vs.like_count, 0) * 3 + COALESCE(vs.comment_count, 0) * 5
                        + COALESCE(vs.favorite_count, 0) * 4) AS hot_score
                FROM video v
                LEFT JOIN video_stat vs ON vs.video_id = v.id
                WHERE v.status = 2 AND v.published_at IS NOT NULL
            ) ranked
            <where>
            <if test="cursor != null">
                hot_score &lt; #{cursor.hotScore}
                OR (hot_score = #{cursor.hotScore} AND published_at &lt; #{cursor.publishedAt})
                OR (hot_score = #{cursor.hotScore} AND published_at = #{cursor.publishedAt} AND id &lt; #{cursor.videoId})
            </if>
            </where>
            ORDER BY hot_score DESC, published_at DESC, id DESC
            LIMIT #{fetchLimit}
            </script>
            """)
    java.util.List<Video> hotPage(@Param("cursor") io.github.gmjneko.gcfeed.modules.feed.model.HotScoreCursor cursor,
                                  @Param("fetchLimit") int fetchLimit);

}
