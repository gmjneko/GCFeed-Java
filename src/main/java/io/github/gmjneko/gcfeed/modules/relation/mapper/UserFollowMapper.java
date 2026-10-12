package io.github.gmjneko.gcfeed.modules.relation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.gmjneko.gcfeed.common.pagination.TimeIdCursor;
import io.github.gmjneko.gcfeed.modules.relation.vo.RelationUserVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

import io.github.gmjneko.gcfeed.modules.relation.entity.UserFollow;

public interface UserFollowMapper extends BaseMapper<UserFollow> {

    @Select("""
            <script>
            SELECT a.id AS user_id, a.nickname, a.avatar_url, a.bio, f.updated_at AS followed_at,
                   EXISTS(SELECT 1 FROM user_follow mine
                          WHERE mine.user_id = #{viewerId} AND mine.target_user_id = a.id AND mine.status = 1) AS following
            FROM user_follow f JOIN account a ON a.id = f.target_user_id AND a.status != 3
            WHERE f.user_id = #{viewerId} AND f.status = 1
            <if test="cursor != null">
              AND (f.updated_at &lt; #{cursor.time} OR (f.updated_at = #{cursor.time} AND f.target_user_id &lt; #{cursor.id}))
            </if>
            ORDER BY f.updated_at DESC, f.target_user_id DESC LIMIT #{fetchLimit}
            </script>
            """)
    List<RelationUserVO> following(@Param("viewerId") Long viewerId, @Param("cursor") TimeIdCursor cursor,
                                   @Param("fetchLimit") int fetchLimit);

    @Select("""
            <script>
            SELECT a.id AS user_id, a.nickname, a.avatar_url, a.bio, f.updated_at AS followed_at,
                   EXISTS(SELECT 1 FROM user_follow mine
                          WHERE mine.user_id = #{viewerId} AND mine.target_user_id = a.id AND mine.status = 1) AS following
            FROM user_follow f JOIN account a ON a.id = f.user_id AND a.status != 3
            WHERE f.target_user_id = #{viewerId} AND f.status = 1
            <if test="cursor != null">
              AND (f.updated_at &lt; #{cursor.time} OR (f.updated_at = #{cursor.time} AND f.user_id &lt; #{cursor.id}))
            </if>
            ORDER BY f.updated_at DESC, f.user_id DESC LIMIT #{fetchLimit}
            </script>
            """)
    List<RelationUserVO> followers(@Param("viewerId") Long viewerId, @Param("cursor") TimeIdCursor cursor,
                                   @Param("fetchLimit") int fetchLimit);

}
