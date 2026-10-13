package io.github.gmjneko.gcfeed.modules.embedding.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.gmjneko.gcfeed.modules.embedding.entity.VideoEmbedding;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface VideoEmbeddingMapper extends BaseMapper<VideoEmbedding> {

    /**
     * 按 {@code uk_video_model} upsert 视频向量，重复生成覆盖同模型旧向量。
     *
     * <p>手写理由：数据库专有语法（判据四）。MyBatis-Plus 的 {@code insertOrUpdate} 是「先查存在
     * 与否再决定 insert/update」的两步操作，中间有竞态窗口；而发布事件与补偿任务可能同时处理同
     * 一个视频，因此必须用数据库的单语句原子 upsert。
     *
     * <p>两种写法按 {@code databaseId} 分支：MySQL 8.0.20 起 {@code VALUES(col)} 已废弃，
     * 改用 {@code AS new} 别名形式（H2 不认）；H2 的 MySQL 兼容模式只支持旧写法。
     * 生产跑 MySQL，测试跑 H2，两边都需要能通过。
     *
     * <p>不更新 {@code updated_at}：列上已有 {@code ON UPDATE CURRENT_TIMESTAMP}，交给数据库维护。
     */
    @Insert("""
            <script>
            INSERT INTO video_embedding (video_id, model, dimension, embedding_json, text_hash)
            VALUES (#{videoId}, #{model}, #{dimension}, #{embeddingJson}, #{textHash})
            <choose>
              <when test="_databaseId == 'h2'">
            ON DUPLICATE KEY UPDATE
                dimension = VALUES(dimension),
                embedding_json = VALUES(embedding_json),
                text_hash = VALUES(text_hash)
              </when>
              <otherwise>
            AS new ON DUPLICATE KEY UPDATE
                dimension = new.dimension,
                embedding_json = new.embedding_json,
                text_hash = new.text_hash
              </otherwise>
            </choose>
            </script>
            """)
    int upsertEmbedding(@Param("videoId") Long videoId,
                        @Param("model") String model,
                        @Param("dimension") Integer dimension,
                        @Param("embeddingJson") String embeddingJson,
                        @Param("textHash") String textHash);

    /**
     * 按视频 + 模型读取单个向量记录。
     *
     * <p>按规范 §4.3 的判据，这个单表等值查询本可以用 {@code LambdaQueryWrapper} 表达。
     * 这里有意保留注解 SQL：本模块的读写都围绕 {@code (video_id, model)} 这一对键，
     * 把两条取数语句与 upsert 放在一起看，比分散在 Wrapper 与注解两种写法里更容易核对，
     * 具体原因见 {@code docs/impl/02-向量模块实现.md} 的「与数据库的交互」。
     */
    @Select("SELECT * FROM video_embedding WHERE video_id = #{videoId} AND model = #{model}")
    VideoEmbedding findByVideoAndModel(@Param("videoId") Long videoId, @Param("model") String model);

    /**
     * 批量读取多个视频在同一模型下的向量记录，供推荐召回一次性取齐。
     *
     * <p>与 {@link #findByVideoAndModel} 同样是有意保留的注解 SQL，理由见该方法注释。
     */
    @Select("""
            <script>
            SELECT * FROM video_embedding
            WHERE model = #{model} AND video_id IN
            <foreach collection="videoIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<VideoEmbedding> findByVideoIdsAndModel(@Param("videoIds") List<Long> videoIds,
                                                @Param("model") String model);

    /**
     * 找出上线但缺少该模型向量的视频，供补偿任务补齐。
     *
     * <p>{@code LEFT JOIN ... IS NULL} 而不是 {@code NOT IN}：后者在子查询结果含 NULL 时
     * 会整体返回空集，且数据量上来后执行计划更差。
     *
     * <p>只处理视频 id 升序的前 {@code limit} 条，避免一次扫全表；补偿任务循环调用直到返回空。
     */
    @Select("""
            SELECT v.id
            FROM video v
            LEFT JOIN video_embedding ve ON ve.video_id = v.id AND ve.model = #{model}
            WHERE v.status = 2
              AND ve.video_id IS NULL
            ORDER BY v.id
            LIMIT #{limit}
            """)
    List<Long> findVideoIdsMissingEmbedding(@Param("model") String model, @Param("limit") int limit);

    /**
     * 批量读取视频标题与简介，供补偿任务重建向量输入。
     *
     * <p>手写理由：只取部分列（判据五）。补偿任务批量扫视频，只需向量化输入的两个字段；
     * 用 {@code selectBatchIds} 会把 {@code media_url}、{@code cover_url} 等长字符串一并
     * 取回，白白增加网络与反序列化开销。
     */
    @Select("""
            <script>
            SELECT id, title, description FROM video
            WHERE id IN
            <foreach collection="videoIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<VideoTextRow> findVideoTexts(@Param("videoIds") List<Long> videoIds);

    /** 补偿任务读取向量输入时的投影，避免加载整个 video 实体。 */
    class VideoTextRow {
        private Long id;
        private String title;
        private String description;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
