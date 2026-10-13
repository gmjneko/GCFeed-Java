package io.github.gmjneko.gcfeed.modules.embedding.repository;

import io.github.gmjneko.gcfeed.modules.embedding.entity.VideoEmbedding;
import io.github.gmjneko.gcfeed.modules.embedding.mapper.VideoEmbeddingMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 视频向量的 MyBatis-Plus 持久化实现，不使用 XML Mapper。 */
@Repository
public class VideoEmbeddingRepositoryImpl implements VideoEmbeddingRepository {

    private final VideoEmbeddingMapper mapper;
    private final JsonMapper json;

    public VideoEmbeddingRepositoryImpl(VideoEmbeddingMapper mapper, JsonMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    public int upsert(Long videoId, String model, double[] embedding, String textHash) {
        return mapper.upsertEmbedding(videoId, model, embedding.length, encode(embedding), textHash);
    }

    @Override
    public Optional<VideoEmbedding> findByVideoAndModel(Long videoId, String model) {
        return Optional.ofNullable(mapper.findByVideoAndModel(videoId, model));
    }

    @Override
    public Map<Long, double[]> findVectors(List<Long> videoIds, String model) {
        if (videoIds == null || videoIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, double[]> vectors = new HashMap<>(videoIds.size());
        for (VideoEmbedding row : mapper.findByVideoIdsAndModel(videoIds, model)) {
            double[] vector = decode(row.getEmbeddingJson());
            // 维度对不上的向量直接跳过：模型换版本时新旧向量会共存，此时按「无法比较」处理。
            if (vector.length > 0 && (row.getDimension() == null || row.getDimension() == vector.length)) {
                vectors.put(row.getVideoId(), vector);
            }
        }
        return vectors;
    }

    @Override
    public List<Long> findVideoIdsMissingEmbedding(String model, int limit) {
        return mapper.findVideoIdsMissingEmbedding(model, limit);
    }

    @Override
    public Map<Long, VideoText> findTexts(List<Long> videoIds) {
        if (videoIds == null || videoIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, VideoText> texts = new LinkedHashMap<>(videoIds.size());
        for (VideoEmbeddingMapper.VideoTextRow row : mapper.findVideoTexts(videoIds)) {
            texts.put(row.getId(), new VideoText(row.getId(), row.getTitle(), row.getDescription()));
        }
        return texts;
    }

    private String encode(double[] embedding) {
        return json.writeValueAsString(embedding);
    }

    /**
     * 解析向量 JSON。
     *
     * <p>MySQL 的 JSON 列取出来就是 {@code [0.1,-0.2]} 这样的裸文本；H2 的 JSON 列会
     * 额外包一层 JSON 字符串引号，取出来是 {@code "[0.1,-0.2]"}。这里先判断是否被引号
     * 包住，是则再解一层，让两种数据库的读取结果一致。
     *
     * <p>解析失败返回空数组而不是抛异常：向量损坏只应让这一个视频退化为「无向量」，
     * 不该让整个推荐请求失败，补偿任务会发现并重建它。
     */
    private double[] decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return new double[0];
        }
        String text = raw.strip();
        try {
            if (text.startsWith("\"")) {
                text = json.readValue(text, String.class);
            }
            return json.readValue(text, double[].class);
        } catch (RuntimeException ex) {
            return new double[0];
        }
    }
}
