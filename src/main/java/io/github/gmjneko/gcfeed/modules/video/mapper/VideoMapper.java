package io.github.gmjneko.gcfeed.modules.video.mapper;

import io.github.gmjneko.gcfeed.modules.feed.model.FeedCursor;
import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface VideoMapper extends BaseMapper<Video> {
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

}
