package io.github.gmjneko.gcfeed.modules.embedding;

import io.github.gmjneko.gcfeed.modules.embedding.entity.VideoEmbedding;
import io.github.gmjneko.gcfeed.modules.embedding.repository.VideoEmbeddingRepository;
import io.github.gmjneko.gcfeed.modules.embedding.service.EmbeddingService;
import io.github.gmjneko.gcfeed.modules.embedding.service.HashNgramVectorizer;
import io.github.gmjneko.gcfeed.modules.embedding.service.Vectorizer;
import io.github.gmjneko.gcfeed.modules.embedding.service.VideoPublishedEvent;
import io.github.gmjneko.gcfeed.modules.user.dto.RegisterDTO;
import io.github.gmjneko.gcfeed.modules.user.service.UserService;
import io.github.gmjneko.gcfeed.modules.video.dto.CreateVideoDTO;
import io.github.gmjneko.gcfeed.modules.video.service.VideoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 向量生成的落库行为：发布后事件生成、upsert 覆盖、补偿扫描。
 *
 * <p>覆盖真实的 Mapper 与数据库往返（H2 MySQL 模式），因此能验证 JSON 列的写入读取、
 * {@code ON DUPLICATE KEY UPDATE} 的唯一键冲突路径，以及跨表联查的补偿扫描。
 */
@SpringBootTest
class EmbeddingModuleTest {

    @Autowired
    private EmbeddingService embeddingService;
    @Autowired
    private VideoEmbeddingRepository embeddingRepository;
    @Autowired
    private Vectorizer vectorizer;
    @Autowired
    private VideoService videoService;
    @Autowired
    private UserService userService;
    @Autowired
    private JdbcTemplate jdbc;

    private long authorId;

    @BeforeEach
    void resetData() {
        jdbc.update("DELETE FROM video_embedding");
        jdbc.update("DELETE FROM video_stat");
        jdbc.update("DELETE FROM video");
        jdbc.update("DELETE FROM user_relation_stat");
        jdbc.update("DELETE FROM account");
        authorId = userService.register(new RegisterDTO("embedding-author", "secret", "author")).id();
    }

    @Test
    void publishCreatesEmbeddingRow() {
        long videoId = publish("first video", "hello timeline");

        VideoEmbedding row = embeddingRepository.findByVideoAndModel(videoId, vectorizer.model()).orElseThrow();
        assertThat(row.getModel()).isEqualTo(HashNgramVectorizer.MODEL);
        assertThat(row.getDimension()).isEqualTo(HashNgramVectorizer.DEFAULT_DIMENSION);
        assertThat(row.getTextHash()).hasSize(64);

        double[] stored = embeddingRepository.findVectors(List.of(videoId), vectorizer.model()).get(videoId);
        assertThat(stored).hasSize(HashNgramVectorizer.DEFAULT_DIMENSION);
        assertThat(stored).containsExactly(vectorizer.vectorize("first video\nhello timeline"));
    }

    /** 重复生成必须覆盖同一行，不能在唯一键上撞出第二份向量。 */
    @Test
    void regenerateOverwritesSameRow() {
        long videoId = publish("first video", "hello");
        Long firstId = embeddingRepository.findByVideoAndModel(videoId, vectorizer.model()).orElseThrow().getId();

        assertThat(embeddingService.generateForPublishedVideo(
                new VideoPublishedEvent(UUID.randomUUID().toString(), videoId, authorId, "second title", "changed",
                        Instant.now()))).isTrue();

        VideoEmbedding row = embeddingRepository.findByVideoAndModel(videoId, vectorizer.model()).orElseThrow();
        assertThat(row.getId()).isEqualTo(firstId);
        assertThat(embeddingRepository.findVectors(List.of(videoId), vectorizer.model()).get(videoId))
                .containsExactly(vectorizer.vectorize("second title\nchanged"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM video_embedding WHERE video_id = ?", Integer.class, videoId))
                .isEqualTo(1);
    }

    @Test
    void unusableEventIsIgnored() {
        assertThat(embeddingService.generateForPublishedVideo(null)).isFalse();
        assertThat(embeddingService.generateForPublishedVideo(
                new VideoPublishedEvent("e", 0L, authorId, "title", null, Instant.now()))).isFalse();
        assertThat(embeddingService.generateForPublishedVideo(
                new VideoPublishedEvent("e", 1L, authorId, "   ", null, Instant.now()))).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM video_embedding", Integer.class)).isZero();
    }

    /** 补偿扫描只挑上线且缺向量的视频，逐批处理直到补齐。 */
    @Test
    void backfillFillsMissingAndSkipsExisting() {
        long withVector = publish("has vector", "already generated");
        long missing = publish("no vector", "missing");
        long offline = publish("offline video", "offline");
        jdbc.update("DELETE FROM video_embedding WHERE video_id = ?", missing);
        jdbc.update("UPDATE video SET status = 3 WHERE id = ?", offline);
        jdbc.update("DELETE FROM video_embedding WHERE video_id = ?", offline);

        assertThat(embeddingRepository.findVideoIdsMissingEmbedding(vectorizer.model(), 10))
                .containsExactly(missing);

        assertThat(embeddingService.backfillMissing(1)).isEqualTo(1);
        assertThat(embeddingRepository.findVideoIdsMissingEmbedding(vectorizer.model(), 10)).isEmpty();
        assertThat(embeddingRepository.findVectors(List.of(withVector), vectorizer.model())).hasSize(1);
        assertThat(embeddingRepository.findVectors(List.of(missing), vectorizer.model()).get(missing))
                .containsExactly(vectorizer.vectorize("no vector\nmissing"));
        // 下架视频不参与补偿：它不会进入推荐候选，生成向量没有意义。
        assertThat(embeddingRepository.findByVideoAndModel(offline, vectorizer.model())).isEmpty();
    }

    @Test
    void backfillWithNonPositiveLimitDoesNothing() {
        publish("title", "description");
        assertThat(embeddingService.backfillMissing(0)).isZero();
    }

    /** 批量读取向量时，一个视频没有向量不影响其他视频的结果。 */
    @Test
    void findVectorsSkipsMissingEntries() {
        long withVector = publish("title one", "description one");
        long withoutVector = publish("title two", "description two");
        jdbc.update("DELETE FROM video_embedding WHERE video_id = ?", withoutVector);

        Map<Long, double[]> vectors = embeddingRepository.findVectors(
                List.of(withVector, withoutVector), vectorizer.model());
        assertThat(vectors).containsOnlyKeys(withVector);
        assertThat(embeddingRepository.findVectors(List.of(), vectorizer.model())).isEmpty();
    }

    private long publish(String title, String description) {
        return videoService.createPublished(authorId,
                new CreateVideoDTO(title, description, "/uploads/video/test.mp4", "/uploads/cover/test.png"),
                null).video().id();
    }
}
