package io.github.gmjneko.gcfeed.modules.embedding.repository;

import io.github.gmjneko.gcfeed.modules.embedding.entity.VideoEmbedding;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 视频向量的持久化边界。
 *
 * <p>向量在数据库里是 JSON 文本，在内存里是 {@code double[]}。转换放在这一层完成，
 * Service 和推荐模块只看到数组，不感知存储格式。
 */
public interface VideoEmbeddingRepository {

    /** 按视频 + 模型 upsert 向量，重复生成覆盖旧值。 */
    int upsert(Long videoId, String model, double[] embedding, String textHash);

    Optional<VideoEmbedding> findByVideoAndModel(Long videoId, String model);

    /** 批量读取向量，返回 {@code videoId -> 向量} 映射；缺失或格式损坏的视频不出现在结果里。 */
    Map<Long, double[]> findVectors(List<Long> videoIds, String model);

    /** 找出上线但没有该模型向量的视频 id，最多 {@code limit} 条，按 id 升序。 */
    List<Long> findVideoIdsMissingEmbedding(String model, int limit);

    /** 批量读取视频标题与简介，用于重建向量输入。 */
    Map<Long, VideoText> findTexts(List<Long> videoIds);

    /** 向量化所需的最小视频字段投影。 */
    record VideoText(Long videoId, String title, String description) {
    }
}
