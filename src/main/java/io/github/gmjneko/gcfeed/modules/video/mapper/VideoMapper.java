package io.github.gmjneko.gcfeed.modules.video.mapper;

import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

public interface VideoMapper extends BaseMapper<Video> {

    /**
     * 读取删除墓碑，供 DELETE 的业务幂等和作者校验使用。
     *
     * <p>该查询与旧发布键兼容查询需要绕过 MyBatis-Plus 全局逻辑删除过滤；常规查询通过
     * BaseMapper + Wrapper 执行，并自动排除 deleted = 1 的行。
     */
    @Select("""
            SELECT id, author_id, title, description, media_url, cover_url,
                   status, published_at, idempotency_key, deleted,
                   created_at, updated_at
            FROM video
            WHERE id = #{videoId}
            """)
    Video selectAnyStatusById(@Param("videoId") Long videoId);
    /** 旧发布键兼容查询包含墓碑，不能因默认逻辑删除过滤而重复插入同一个键。 */
    @Select("""
            SELECT id, author_id, title, description, media_url, cover_url, status,
                   published_at, idempotency_key, deleted, created_at, updated_at
            FROM video WHERE author_id = #{authorId} AND idempotency_key = #{key}
            """)
    Video selectByLegacyKey(@Param("authorId") Long authorId, @Param("key") String key);
}
