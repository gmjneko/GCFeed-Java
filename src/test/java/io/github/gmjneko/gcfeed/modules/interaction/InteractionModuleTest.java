package io.github.gmjneko.gcfeed.modules.interaction;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.constant.RedisKeyConstant;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.common.jwt.JwtUtil;
import io.github.gmjneko.gcfeed.modules.interaction.job.InteractionStatReconcileJob;
import io.github.gmjneko.gcfeed.modules.interaction.repository.InteractionRepository;
import io.github.gmjneko.gcfeed.modules.interaction.service.InteractionService;
import io.github.gmjneko.gcfeed.modules.interaction.vo.ActionVO;
import io.github.gmjneko.gcfeed.modules.video.InMemoryVideoStatDeltaBuffer;
import io.github.gmjneko.gcfeed.modules.video.dto.CreateVideoDTO;
import io.github.gmjneko.gcfeed.modules.video.job.VideoStatFlushJob;
import io.github.gmjneko.gcfeed.modules.video.model.VideoStatField;
import io.github.gmjneko.gcfeed.modules.video.repository.VideoRepository;
import io.github.gmjneko.gcfeed.modules.video.service.VideoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.IntConsumer;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(InteractionModuleTest.BufferConfiguration.class)
class InteractionModuleTest {
    @Autowired private InteractionService service;
    @Autowired private VideoService videos;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JsonMapper json;
    @Autowired private JwtUtil jwt;
    @Autowired private InMemoryVideoStatDeltaBuffer buffer;
    @Autowired private VideoStatFlushJob flushJob;
    @Autowired private InteractionStatReconcileJob reconcileJob;
    @MockitoBean private RedissonClient redis;
    @MockitoSpyBean private InteractionRepository repository;
    @MockitoSpyBean private VideoRepository videoRepository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void resetData() {
        for (String table : List.of("interaction_action", "video_stat", "video", "user_relation_stat", "account")) {
            jdbc.update("DELETE FROM " + table);
        }
        buffer.clear();
        for (int id = 1; id <= 12; id++) {
            jdbc.update("INSERT INTO account (id, account, password, nickname) VALUES (?, ?, 'unused', 'tester')", id, "action-" + id);
        }
        for (int id = 1; id <= 2; id++) {
            jdbc.update("INSERT INTO video (id, author_id, title, media_url, cover_url, published_at) VALUES (?, 1, 'title', 'media', 'cover', CURRENT_TIMESTAMP)", id);
            jdbc.update("INSERT INTO video_stat (video_id) VALUES (?)", id);
        }
        RBucket<Object> bucket = mock(RBucket.class);
        when(redis.getBucket(anyString())).thenReturn(bucket);
        when(bucket.isExists()).thenReturn(false);
        when(bucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);
    }

    @Test
    void allFourEndpointsSetStateRatherThanToggleAndFeedReflectsIt() throws Exception {
        String token = jwt.signAccessToken(1L, "user");
        for (String action : List.of("like", "favorite")) {
            for (int repeat = 0; repeat < 2; repeat++) {
                mvc.perform(put("/api/videos/1/" + action).header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                        .andExpect(jsonPath("$.data.action_type").value(action.toUpperCase()))
                        .andExpect(jsonPath("$.data.active").value(true))
                        .andExpect(jsonPath("$.data." + action + "_count").value(1));
            }
        }
        assertThat(feedItem(1L, token).path("liked").asBoolean()).isTrue();
        assertThat(feedItem(1L, token).path("favorited").asBoolean()).isTrue();
        assertThat(feedItem(1L, null).path("liked").asBoolean()).isFalse();
        flushJob.flush();
        mvc.perform(get("/api/videos/1")).andExpect(jsonPath("$.data.like_count").value(1))
                .andExpect(jsonPath("$.data.favorite_count").value(1));
        for (String action : List.of("like", "favorite")) {
            for (int repeat = 0; repeat < 2; repeat++) {
                mvc.perform(delete("/api/videos/1/" + action).header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(false))
                        .andExpect(jsonPath("$.data." + action + "_count").value(0));
            }
        }
        assertThat(service.like(1L, 1L, null).likeCount()).isEqualTo(1);
        assertThat(service.favorite(1L, 1L, null).favoriteCount()).isEqualTo(1);
    }

    @Test
    void authenticationValidationAndVisibilityAreEnforced() throws Exception {
        for (String action : List.of("like", "favorite")) {
            mvc.perform(put("/api/videos/1/" + action)).andExpect(status().isUnauthorized());
            mvc.perform(delete("/api/videos/1/" + action).header("Authorization", "Bearer bad"))
                    .andExpect(status().isUnauthorized());
        }
        String token = jwt.signAccessToken(2L, "user");
        mvc.perform(put("/api/videos/0/like").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(20001));
        mvc.perform(put("/api/videos/bad/like").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/videos/99/like").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        for (int state : List.of(1, 3, 4)) {
            jdbc.update("UPDATE video SET status = ? WHERE id = 1", state);
            assertBusiness(() -> service.like(2L, 1L, "visibility"), ErrorCode.VIDEO_NOT_FOUND);
            assertBusiness(() -> service.unlike(2L, 1L, null), ErrorCode.VIDEO_NOT_FOUND);
        }
        assertThat(count("interaction_action")).isZero();
        assertThat(buffer.pending(1L)).isEmpty();
    }

    @Test
    void duplicateRequestKeyIsRejectedWithoutRunningTheBusinessOperation() throws Exception {
        String token = jwt.signAccessToken(2L, "user");
        String redisKey = RedisKeyConstant.DUPLICATE_SUBMIT.formatted("interaction.like", 2L, "in-flight");
        RBucket<Object> marker = mock(RBucket.class);
        when(redis.getBucket(redisKey)).thenReturn(marker);
        when(marker.setIfAbsent(anyString(), any(Duration.class))).thenReturn(false);

        mvc.perform(put("/api/videos/1/like")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "in-flight"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_SUBMISSION.getCode()));
        assertThat(count("interaction_action")).isZero();
        assertThat(flushedLikeCount()).isZero();
    }

    @Test
    void interactionRetriesUseBusinessStateInsteadOfHistoricalSnapshots() throws Exception {
        ActionVO first = service.like(2L, 1L, " A ");
        assertThat(first.active()).isTrue();
        assertThat(service.unlike(2L, 1L, "B").active()).isFalse();
        // No old response snapshot is replayed; the repeated PUT sets the current state again.
        assertThat(service.like(2L, 1L, "A").active()).isTrue();
        assertThat(flushedLikeCount()).isEqualTo(1);
        assertThat(activeLikes()).isEqualTo(1);
        String token = jwt.signAccessToken(2L, "user");
        mvc.perform(put("/api/videos/1/like").header("Authorization", "Bearer " + token).header("Idempotency-Key", "A"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(true));
        assertThat(feedItem(1L, token).path("liked").asBoolean()).isTrue();
        // Repeating the same target state with a different key still changes the counter only once.
        service.like(3L, 1L, "A");
        service.favorite(2L, 1L, "A");
        service.like(3L, 1L, "different");
        assertThat(flushedLikeCount()).isEqualTo(2);
        assertThat(activeLikes()).isEqualTo(2);
    }

    @Test
    void firstCancelIsNoOpAndLaterChangesFlipOneRow() {
        assertThat(service.unlike(2L, 1L, null).likeCount()).isZero();
        service.unfavorite(2L, 1L, null);
        assertThat(count("interaction_action")).isZero();
        assertThat(buffer.pending(1L)).isEmpty();
        assertThat(service.like(2L, 1L, null).likeCount()).isEqualTo(1);
        assertThat(service.unlike(2L, 1L, null).likeCount()).isZero();
        assertThat(service.like(2L, 1L, null).likeCount()).isEqualTo(1);
        assertThat(count("interaction_action")).isEqualTo(1);
        assertThat(flushedLikeCount()).isEqualTo(1);
        // 计数被外部改小时，减量由 GREATEST 钳制，不会刷成负数。
        jdbc.update("UPDATE video_stat SET like_count = 0 WHERE video_id = 1");
        assertThat(service.unlike(2L, 1L, null).likeCount()).isZero();
        assertThat(flushedLikeCount()).isZero();
    }

    @Test
    void countsStayBufferedUntilFlushAndResponsesIncludePendingDeltas() {
        assertThat(service.like(2L, 1L, null).likeCount()).isEqualTo(1);
        assertThat(service.like(3L, 1L, null).likeCount()).isEqualTo(2);
        assertThat(rawLikeCount()).isZero();
        assertThat(buffer.pending(1L)).containsEntry(VideoStatField.LIKE_COUNT, 2L);
        flushJob.flush();
        assertThat(rawLikeCount()).isEqualTo(2);
        assertThat(buffer.pending(1L)).isEmpty();
        assertThat(service.unlike(3L, 1L, null).likeCount()).isEqualTo(1);
    }

    @Test
    void rolledBackTransactionsLeaveNoActionOrDelta() {
        doThrow(new IllegalStateException("injected failure")).when(repository).findStat(1L);
        assertThatThrownBy(() -> service.like(2L, 1L, null)).isInstanceOf(IllegalStateException.class);
        assertThat(count("interaction_action")).isZero();
        assertThat(buffer.pending(1L)).isEmpty();
        doCallRealMethod().when(repository).findStat(1L);
        assertThat(service.like(2L, 1L, null).likeCount()).isEqualTo(1);
    }

    @Test
    void failedFlushPutsDeltasBackForTheNextRound() {
        service.like(2L, 1L, null);
        doThrow(new IllegalStateException("db down")).when(videoRepository).addStatDeltas(eq(1L), any());
        flushJob.flush();
        assertThat(rawLikeCount()).isZero();
        assertThat(buffer.pending(1L)).containsEntry(VideoStatField.LIKE_COUNT, 1L);
        doCallRealMethod().when(videoRepository).addStatDeltas(eq(1L), any());
        flushJob.flush();
        assertThat(rawLikeCount()).isEqualTo(1);
    }

    @Test
    void reconciliationRebuildsCountsFromActiveActionsWithoutDoubleCountingPendingDeltas() {
        service.like(2L, 1L, null);
        service.like(3L, 1L, null);
        service.favorite(2L, 1L, null);
        flushJob.flush();
        jdbc.update("UPDATE video_stat SET like_count = 99, favorite_count = 0 WHERE video_id = 1");
        jdbc.update("UPDATE video_stat SET like_count = 5 WHERE video_id = 2");
        service.like(4L, 1L, null);

        reconcileJob.reconcile();
        // 3 个有效点赞中有 1 个还在缓冲里，库里先修正为 2，刷库后恰好为 3。
        assertThat(rawLikeCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT favorite_count FROM video_stat WHERE video_id = 1", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT like_count FROM video_stat WHERE video_id = 2", Integer.class))
                .isZero();
        flushJob.flush();
        assertThat(rawLikeCount()).isEqualTo(3);
    }

    @Test
    void concurrentSameAndDifferentKeysCountEachUserOnlyOnce() throws Exception {
        parallel(8, i -> service.like(2L, 1L, "same"));
        assertThat(flushedLikeCount()).isEqualTo(1);
        parallel(8, i -> service.like(2L, 1L, "key-" + i));
        assertThat(flushedLikeCount()).isEqualTo(1);
        parallel(8, i -> service.like((long) i + 3, 1L, "same"));
        assertThat(flushedLikeCount()).isEqualTo(9);
        assertThat(activeLikes()).isEqualTo(9);
        parallel(8, i -> service.unlike(2L, 1L, "cancel-" + i));
        assertThat(flushedLikeCount()).isEqualTo(8);
        parallel(8, i -> {
            if (i % 2 == 0) service.like(2L, 1L, null);
            else service.unlike(2L, 1L, null);
        });
        assertThat(flushedLikeCount()).isEqualTo(activeLikes());
    }

    @Test
    void videoRequestKeyUsesBusinessRecordWithoutStoringGenericSnapshots() {
        jdbc.update("UPDATE video SET idempotency_key = 'legacy', description = '' WHERE id = 1");
        var dto = new CreateVideoDTO("title", "", "media", "cover");
        assertBusiness(() -> videos.createPublished(1L, new CreateVideoDTO("changed", "", "media", "cover"), "legacy"),
                ErrorCode.PUBLISH_REQUEST_CONFLICT);
        var legacy = videos.createPublished(1L, dto, "legacy");
        assertThat(legacy.created()).isFalse();
        assertThat(legacy.video().id()).isEqualTo(1L);
        assertThat(count("video")).isEqualTo(2);
        service.like(2L, 1L, null);
        flushJob.flush();
        assertThat(videos.createPublished(1L, dto, "legacy").video().likeCount()).isEqualTo(1);
        var fresh = videos.createPublished(1L, dto, "fresh");
        assertThat(fresh.created()).isTrue();
        assertThat(videos.createPublished(1L, dto, "fresh").created()).isFalse();
        videos.delete(1L, fresh.video().id());
        var replay = videos.createPublished(1L, dto, "fresh");
        assertThat(replay.created()).isFalse();
        assertThat(replay.video().id()).isEqualTo(fresh.video().id());
        assertThat(replay.video().status()).isEqualTo(4);
        assertThat(count("video")).isEqualTo(3);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private JsonNode feedItem(Long videoId, String token) throws Exception {
        var request = get("/api/feed-items");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        JsonNode items = json.readTree(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data").path("items");
        for (JsonNode item : items) {
            if (item.path("video_id").asLong() == videoId) {
                return item;
            }
        }
        throw new AssertionError("feed item not found for video " + videoId);
    }

    /** 先刷库再读，断言最终一致后的计数。 */
    private int flushedLikeCount() {
        flushJob.flush();
        return rawLikeCount();
    }

    private int rawLikeCount() {
        return jdbc.queryForObject("SELECT like_count FROM video_stat WHERE video_id = 1", Integer.class);
    }

    private int activeLikes() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM interaction_action WHERE video_id = 1 AND action_type = 'LIKE' AND status = 1", Integer.class);
    }

    private void assertBusiness(org.assertj.core.api.ThrowableAssert.ThrowingCallable work, ErrorCode expected) {
        assertThatThrownBy(work).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }

    private void parallel(int threads, IntConsumer work) throws Exception {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(pool.submit(() -> { ready.countDown(); await(start); work.accept(index); }));
            }
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("latch timed out");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class BufferConfiguration {
        @Bean
        @Primary
        InMemoryVideoStatDeltaBuffer inMemoryVideoStatDeltaBuffer() {
            return new InMemoryVideoStatDeltaBuffer();
        }
    }
}
