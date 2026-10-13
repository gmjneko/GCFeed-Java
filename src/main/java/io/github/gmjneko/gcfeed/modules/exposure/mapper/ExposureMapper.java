package io.github.gmjneko.gcfeed.modules.exposure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.gmjneko.gcfeed.modules.exposure.entity.Exposure;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/** 按用户与视频保存曝光聚合，唯一键保证同一维度只有一行。 */
public interface ExposureMapper extends BaseMapper<Exposure> {

    /**
     * 原子记录一次曝光，保留首次时间并累加次数。
     *
     * <p>手写理由：数据库方言或专有语法（规范 §4.3 判据四）。先查再 insert/update
     * 会产生竞态；必须依赖 uk_user_video 与单语句原子自增防止并发丢计数。
     * MySQL 使用 AS new，H2 MySQL 模式使用 VALUES(col)，共用现有 databaseId 分支。
     *
     * <p>原始 SQL 不走实体字段自动填充，created_at 使用数据库默认值，更新时显式刷新 updated_at。
     * 返回值是影响行数，重复更新可能返回 2，不代表最终曝光次数。
     */
    @Insert("""
            <script>
            INSERT INTO exposures
                (user_id, video_id, first_exposed_at, last_exposed_at, exposure_count, last_scene)
            VALUES (#{exposure.userId}, #{exposure.videoId}, #{exposure.firstExposedAt},
                    #{exposure.lastExposedAt}, 1, #{exposure.lastScene})
            <choose>
              <when test="_databaseId == 'h2'">
            ON DUPLICATE KEY UPDATE
                last_exposed_at = VALUES(last_exposed_at),
                exposure_count = exposure_count + 1,
                last_scene = VALUES(last_scene),
                updated_at = CURRENT_TIMESTAMP
              </when>
              <otherwise>
            AS new ON DUPLICATE KEY UPDATE
                last_exposed_at = new.last_exposed_at,
                exposure_count = exposures.exposure_count + 1,
                last_scene = new.last_scene,
                updated_at = CURRENT_TIMESTAMP
              </otherwise>
            </choose>
            </script>
            """)
    int upsertExposure(@Param("exposure") Exposure exposure);

}
