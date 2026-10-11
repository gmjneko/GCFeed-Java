package io.github.gmjneko.gcfeed.modules.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.gmjneko.gcfeed.modules.interaction.entity.InteractionAction;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;

public interface InteractionActionMapper extends BaseMapper<InteractionAction> {
    /** 包含逻辑删除行，避免把持有唯一键的旧行为误当作不存在。调用前先锁定视频。 */
    @Select("""
            SELECT id, user_id, video_id, action_type, status, deleted, created_at, updated_at
            FROM interaction_action
            WHERE user_id = #{userId} AND video_id = #{videoId} AND action_type = #{type}
            FOR UPDATE
            """)
    InteractionAction selectAnyStateForUpdate(@Param("userId") Long userId, @Param("videoId") Long videoId,
                                               @Param("type") String type);

    /** 原生更新用于恢复墓碑；BaseMapper 的全局逻辑删除条件会排除此行。 */
    @Update("""
            UPDATE interaction_action SET status = #{status}, deleted = 0, updated_at = #{now}
            WHERE id = #{id}
            """)
    int restoreState(@Param("id") Long id, @Param("status") int status, @Param("now") LocalDateTime now);
}
