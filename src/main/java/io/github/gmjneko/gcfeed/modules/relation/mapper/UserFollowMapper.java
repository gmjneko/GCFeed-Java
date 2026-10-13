package io.github.gmjneko.gcfeed.modules.relation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.gmjneko.gcfeed.common.pagination.TimeIdCursor;
import io.github.gmjneko.gcfeed.modules.relation.vo.RelationUserVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

import io.github.gmjneko.gcfeed.modules.relation.entity.UserFollow;

public interface UserFollowMapper extends BaseMapper<UserFollow> {

    /**
     * 按游标查询当前用户关注的人。
     *
     * <p>手写理由：多表 JOIN（判据一）加 SQL 内计算列（判据三）。{@code JOIN account} 是为了
     * 过滤已注销账号并取展示资料，{@code EXISTS(...) AS following} 让每行带出「我是否也关注了
     * 这个人」——换成 Wrapper 需要先查一页关系、再查账号、再查一次关注状态，一次查询变三次。
     */
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

    /**
     * 按游标查询当前用户的粉丝，与关注列表同构，只是关系方向相反。
     *
     * <p>手写理由同 {@link #following}：多表 JOIN 加计算列。
     */
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
