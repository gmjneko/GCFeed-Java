package io.github.gmjneko.gcfeed.modules.recommendation;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.common.jwt.JwtUtil;
import io.github.gmjneko.gcfeed.modules.embedding.repository.VideoEmbeddingRepository;
import io.github.gmjneko.gcfeed.modules.embedding.service.Vectorizer;
import io.github.gmjneko.gcfeed.modules.exposure.dto.BatchExposureDTO;
import io.github.gmjneko.gcfeed.modules.exposure.repository.ExposureRepository;
import io.github.gmjneko.gcfeed.modules.exposure.service.ExposureService;
import io.github.gmjneko.gcfeed.modules.feed.repository.FeedRepository;
import io.github.gmjneko.gcfeed.modules.recommendation.cache.RedisRecommendationCache;
import io.github.gmjneko.gcfeed.modules.recommendation.cache.RecommendationCache;
import io.github.gmjneko.gcfeed.modules.recommendation.dto.QueryRecommendationDTO;
import io.github.gmjneko.gcfeed.modules.recommendation.model.RecommendationCursor;
import io.github.gmjneko.gcfeed.modules.recommendation.repository.RecommendationRepository;
import io.github.gmjneko.gcfeed.modules.recommendation.service.RecommendationService;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import java.sql.Connection;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 真实 H2 查询与事务，Redis 替身按键保存真实 JSON 并以注入时钟检查过期。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(RecommendationModuleTest.SqlConfiguration.class)
class RecommendationModuleTest {

    private static final Instant TIME = Instant.parse("2026-10-13T12:00:00Z");
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JsonMapper json;
    @Autowired private SqlRecorder sql;
    @Autowired private JwtUtil jwt;
    @Autowired private RecommendationService service;
    @Autowired private ExposureService exposureService;
    @Autowired private Vectorizer vectorizer;
    @Autowired private VideoEmbeddingRepository embeddings;
    @MockitoBean private RedissonClient redis;
    @MockitoBean private Clock clock;
    @MockitoSpyBean private RecommendationCache cache;
    @MockitoSpyBean private ExposureRepository exposures;
    @MockitoSpyBean private RecommendationRepository recommendations;
    @MockitoSpyBean private FeedRepository feed;
    private Instant now;
    private final Map<String, String> values = new ConcurrentHashMap<>();
    private final Map<String, Instant> expiry = new ConcurrentHashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void reset() {
        now = TIME;
        when(clock.instant()).thenAnswer(i -> now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        values.clear();
        expiry.clear();
        for (String table : List.of("video_view_events", "exposures", "video_embedding", "interaction_action", "user_follow",
                "video_stat", "video", "user_relation_stat", "account")) jdbc.update("DELETE FROM " + table);
        for (long id = 1; id <= 3; id++) jdbc.update("INSERT INTO account(id, account, password, nickname) VALUES (?, ?, 'x', ?)",
                id, "recommend-" + id, "author-" + id);
        RBucket<Object> marker = mock(RBucket.class);
        when(redis.getBucket(anyString())).thenReturn(marker);
        when(marker.isExists()).thenReturn(false);
        when(redis.<String>getBucket(anyString(), eq(StringCodec.INSTANCE))).thenAnswer(call -> {
            String key = call.getArgument(0);
            RBucket<String> bucket = mock(RBucket.class);
            when(bucket.get()).thenAnswer(i -> expiry.containsKey(key) && !now.isBefore(expiry.get(key)) ? null : values.get(key));
            doAnswer(i -> {
                values.put(key, i.getArgument(0));
                expiry.put(key, now.plus(i.<Duration>getArgument(1)));
                return null;
            }).when(bucket).set(anyString(), any(Duration.class));
            return bucket;
        });
    }

    @Test
    void feedGetAndPostUseTokenAndSharedCardAssembly() throws Exception {
        videos(12);
        jdbc.update("INSERT INTO interaction_action(user_id, video_id, action_type, status) VALUES (2,12,'LIKE',1)");
        jdbc.update("INSERT INTO user_follow(user_id,target_user_id,status) VALUES (2,1,1)");
        mvc.perform(get("/api/feed-items").param("scene", "recommend")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/feed-queries").contentType("application/json").content("{\"scene\":\"recommend\"}"))
                .andExpect(status().isUnauthorized());
        JsonNode first = json.readTree(mvc.perform(get("/api/feed-items").param("scene", " Recommend ")
                .header("Authorization", token())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        assertThat(first.get("items").size()).isEqualTo(10);
        assertThat(first.get("items").get(0).get("video_id").asLong()).isEqualTo(12);
        assertThat(first.get("items").get(0).get("liked").asBoolean()).isTrue();
        assertThat(first.get("items").get(0).get("following").asBoolean()).isTrue();
        clearInvocations(feed);
        mvc.perform(post("/api/feed-queries").header("Authorization", token()).contentType("application/json")
                .content("{\"scene\":\"recommend\",\"user_id\":3,\"limit\":1000,\"context\":{\"request_id\":\" req \"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(12));
        verify(feed, times(1)).findAuthors(any());
        verify(feed, times(1)).findStats(any());
        verify(feed, times(1)).findViewerActions(eq(2L), any());
        assertThat(values.keySet()).anyMatch(k -> k.startsWith("recommend:profile:") && k.endsWith(":2"));
        mvc.perform(post("/api/feed-queries").contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.scene").value("timeline"));
    }

    @Test
    void internalTokenProtectsAllRoutesWithoutJwt() throws Exception {
        for (String route : List.of("recommendation-candidates", "exposure-decisions", "exposures")) {
            for (String token : List.of("", "wrong")) mvc.perform(post("/internal/" + route)
                    .header("X-Internal-Token", token).contentType("application/json").content("{}"))
                    .andExpect(status().isUnauthorized());
        }
        internal("recommendation-candidates", "{\"user_id\":2,\"scene\":\"custom\"}", 200)
                .andExpect(jsonPath("$.data.candidates").isEmpty()).andExpect(jsonPath("$.data.request_id").doesNotExist());
    }

    @Test
    void snapshotFreezesRankAcrossClockStatisticsProfileAndNewVideos() {
        videos(6);
        var first = query("", 2);
        assertThat(first.candidates()).extracting(c -> c.videoId()).containsExactly(6L, 5L);
        RecommendationCursor cursor = RecommendationCursor.decode(first.nextCursor(), json);
        Instant originalExpiry = expiry.get(RedisRecommendationCache.snapshotKey(cursor.snapshotId()));
        jdbc.update("UPDATE video_stat SET like_count=100000 WHERE video_id=1");
        video(7, 1);
        now = now.plusSeconds(600);
        var second = query(first.nextCursor(), 3);
        assertThat(second.candidates()).extracting(c -> c.videoId()).containsExactly(4L, 3L, 2L);
        assertThat(expiry.get(RedisRecommendationCache.snapshotKey(cursor.snapshotId()))).isEqualTo(originalExpiry);
        var third = query(second.nextCursor(), 1);
        assertThat(third.candidates()).extracting(c -> c.videoId()).containsExactly(1L);
        assertThat(third.hasMore()).isFalse();
        assertThat(query(third.nextCursor(), 5).candidates()).isEmpty();
        assertThat(query("", 1).candidates().getFirst().videoId()).isEqualTo(1L);
        verify(recommendations, times(2)).recall(anyLong(), any(), eq(500));
    }

    @Test
    void laterPagesSkipPublicAndInternalExposureAndOfflineVideosWithoutStalling() throws Exception {
        videos(6);
        var first = query("", 1);
        exposureService.saveExposures(new BatchExposureDTO(2L, "hot", null, List.of(5L)));
        mvc.perform(post("/api/video-view-events").header("Authorization", token()).contentType("application/json")
                .content("{\"video_id\":4,\"scene\":\"timeline\",\"event_type\":\"exposed\"}")).andExpect(status().isCreated());
        jdbc.update("UPDATE video SET status=3 WHERE id=3");
        var second = query(first.nextCursor(), 2);
        assertThat(second.candidates()).extracting(c -> c.videoId()).containsExactly(2L, 1L);
        assertThat(second.hasMore()).isFalse();
    }

    @Test
    void exhaustedFilteredSnapshotReturnsEmptyPage() {
        videos(3);
        var first = query("", 1);
        exposureService.saveExposures(new BatchExposureDTO(2L, "hot", null, List.of(2L, 1L)));
        var page = query(first.nextCursor(), 2);
        assertThat(page.candidates()).isEmpty();
        assertThat(page.nextCursor()).isEmpty();
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void fixedPoolCapsAt500IndependentOfPageLimit() {
        videos(503);
        var page = query("", 1);
        var cursor = RecommendationCursor.decode(page.nextCursor(), json);
        assertThat(cache.findSnapshot(cursor.snapshotId()).orElseThrow().candidates()).hasSize(500);
        assertThat(page.candidates().getFirst().videoId()).isEqualTo(503);
    }

    @Test
    void invalidForeignAndExpiredCursorAreDistinct() throws Exception {
        videos(2);
        var page = query("", 1);
        assertError(() -> query("broken", 1), ErrorCode.INVALID_CURSOR);
        assertError(() -> service.recommend(new QueryRecommendationDTO(3L, "recommend", null, page.nextCursor(), 1)), ErrorCode.INVALID_CURSOR);
        assertError(() -> service.recommend(new QueryRecommendationDTO(2L, "hot", null, page.nextCursor(), 1)), ErrorCode.INVALID_CURSOR);
        var cursor = RecommendationCursor.decode(page.nextCursor(), json);
        assertError(() -> query(new RecommendationCursor(1, cursor.snapshotId(), 99).encode(json), 1), ErrorCode.INVALID_CURSOR);
        assertError(() -> query(new RecommendationCursor(2, cursor.snapshotId(), 1).encode(json), 1), ErrorCode.INVALID_CURSOR);
        now = now.plusSeconds(1800);
        assertError(() -> query(page.nextCursor(), 1), ErrorCode.RECOMMENDATION_SESSION_EXPIRED);
        mvc.perform(post("/api/feed-queries").header("Authorization", token()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("scene", "recommend", "cursor", page.nextCursor()))))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value(70013));
    }

    @Test
    void coldAndWarmProfilesCacheForFiveMinutesAndRecoverFromCacheFailure() {
        videos(2);
        query("", 1);
        verify(exposures, times(1)).findInterestEvents(eq(2L), any(), eq(200));
        double[] v = new double[vectorizer.dimension()]; v[0] = 1;
        embeddings.upsert(1L, vectorizer.model(), v, "a".repeat(64));
        jdbc.update("INSERT INTO video_view_events(user_id,video_id,scene,event_type,watch_ms,completed,created_at) VALUES (2,1,'recommend','complete',30000,TRUE,?)", LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        query("", 1);
        verify(exposures, times(1)).findInterestEvents(eq(2L), any(), eq(200));
        now = now.plusSeconds(300);
        assertThat(query("", 1).candidates().getFirst().videoId()).isEqualTo(1L);
        verify(exposures, times(2)).findInterestEvents(eq(2L), any(), eq(200));
        doThrow(new IllegalStateException("cache read")).when(cache).findProfile(anyLong(), anyString(), anyInt());
        doThrow(new IllegalStateException("cache write")).when(cache).saveProfile(anyLong(), anyString(), anyInt(), any());
        assertThat(query("", 1).candidates().getFirst().reason()).isEqualTo("interest_match");
    }

    @Test
    void snapshotAndDatabaseFailuresRetainCauseAndReturn500() throws Exception {
        videos(2);
        doThrow(new IllegalStateException("snapshot write")).when(cache).saveSnapshot(anyString(), any());
        assertThatThrownBy(() -> query("", 1)).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LOAD_RECOMMENDATION_FAILED);
            assertThat(ex.getCause()).hasMessage("snapshot write");
        });
        internal("recommendation-candidates", "{\"user_id\":2,\"scene\":\"recommend\"}", 500)
                .andExpect(jsonPath("$.code").value(70011));
    }

    @Test
    void decisionWindowInclusiveCrossSceneDedupAndUnknownVideos() throws Exception {
        videos(2);
        exposureService.saveExposures(new BatchExposureDTO(2L, "timeline", null, List.of(1L, 2L)));
        jdbc.update("UPDATE exposures SET last_exposed_at=? WHERE video_id=1", LocalDateTime.ofInstant(now, ZoneOffset.UTC).minusDays(7));
        jdbc.update("UPDATE exposures SET last_exposed_at=? WHERE video_id=2", LocalDateTime.ofInstant(now, ZoneOffset.UTC).minusDays(7).minusSeconds(1));
        internal("exposure-decisions", "{\"user_id\":2,\"scene\":\" HOT \" ,\"video_ids\":[2,1,2,999]}", 200)
                .andExpect(jsonPath("$.data.scene").value("hot"))
                .andExpect(jsonPath("$.data.decisions[0].video_id").value(2))
                .andExpect(jsonPath("$.data.decisions[0].allowed").value(true))
                .andExpect(jsonPath("$.data.decisions[0].last_exposed_at").doesNotExist())
                .andExpect(jsonPath("$.data.decisions[1].allowed").value(false))
                .andExpect(jsonPath("$.data.decisions[2].video_id").value(999))
                .andExpect(jsonPath("$.data.decisions[2].allowed").value(true));
    }

    @Test
    void emptyBatchesDoNotTouchPersistenceAndMalformedArraysFail() throws Exception {
        clearInvocations(exposures, recommendations);
        internal("exposures", "{\"user_id\":2,\"scene\":\"hot\",\"video_ids\":[]}", 201);
        internal("exposure-decisions", "{\"user_id\":2,\"scene\":\"hot\",\"video_ids\":[]}", 200);
        verify(exposures, times(0)).insertViewEvent(any());
        for (String ids : List.of("null", "[null]", "[0]", "[-1]")) {
            internal("exposures", "{\"user_id\":2,\"scene\":\"hot\",\"video_ids\":" + ids + "}", 400);
        }
    }

    @Test
    void batchWritesBothTablesDedupsAndRestoresInputOrderAndRepeatsIncrement() throws Exception {
        videos(2);
        String body = "{\"user_id\":2,\"scene\":\" Hot \" ,\"request_id\":\" req \" ,\"video_ids\":[2,1,2]}";
        for (int count = 1; count <= 2; count++) {
            internal("exposures", body, 201).andExpect(jsonPath("$.data.exposures[0].video_id").value(2))
                    .andExpect(jsonPath("$.data.exposures[0].exposure_count").value(count))
                    .andExpect(jsonPath("$.data.exposures[1].video_id").value(1));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM video_view_events WHERE user_id=2 AND request_id='req'", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exposures", Integer.class)).isEqualTo(2);
    }

    @Test
    void invalidVideoRollsBackBatchBeforeAnyWrites() throws Exception {
        videos(2);
        for (long id : List.of(3L, 2L)) {
            if (id == 2) jdbc.update("UPDATE video SET status=1 WHERE id=2");
            internal("exposures", "{\"user_id\":2,\"scene\":\"hot\",\"video_ids\":[1," + id + "]}", 404);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM video_view_events", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exposures", Integer.class)).isZero();
        }
    }

    @Test
    void failedBatchUpsertRollsBackNewAndExistingAggregatesAndEvents() {
        videos(3);
        exposureService.saveExposures(new BatchExposureDTO(2L, "timeline", null, List.of(1L)));
        doThrow(new DataAccessResourceFailureException("aggregate")).when(exposures)
                .upsertExposure(org.mockito.ArgumentMatchers.argThat(e -> e.getVideoId() == 3L));
        assertError(() -> exposureService.saveExposures(new BatchExposureDTO(2L, "hot", null, List.of(3L, 2L, 1L))), ErrorCode.SAVE_VIEW_EVENT_FAILED);
        assertThat(jdbc.queryForObject("SELECT exposure_count FROM exposures WHERE video_id=1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_scene FROM exposures WHERE video_id=1", String.class)).isEqualTo("timeline");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exposures", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM video_view_events", Integer.class)).isEqualTo(1);
    }

    @Test
    void failedBatchReadbackRollsBackEverything() {
        videos(2);
        doReturn(Optional.empty()).when(exposures).findByUserAndVideo(2L, 2L);
        assertError(() -> exposureService.saveExposures(new BatchExposureDTO(2L, "hot", null, List.of(1L, 2L))), ErrorCode.SAVE_VIEW_EVENT_FAILED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM video_view_events", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exposures", Integer.class)).isZero();
    }

    @Test
    void reversedConcurrentBatchesDoNotLoseCounts() throws Exception {
        videos(2);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<java.util.concurrent.Future<?>>();
            for (int index = 0; index < 12; index++) {
                var ids = index % 2 == 0 ? List.of(2L, 1L) : List.of(1L, 2L);
                tasks.add(executor.submit(() -> exposureService.saveExposures(new BatchExposureDTO(2L, "hot", null, ids))));
            }
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForList("SELECT exposure_count FROM exposures ORDER BY video_id", Integer.class)).containsExactly(12, 12);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM video_view_events", Integer.class)).isEqualTo(24);
    }

    @Test
    void normalizationUnicodeAndLimitValidation() throws Exception {
        videos(2);
        for (int limit : List.of(0, -1)) internal("recommendation-candidates", "{\"user_id\":2,\"scene\":\"hot\",\"limit\":" + limit + "}", 400);
        for (long user : List.of(0L, -1L)) internal("recommendation-candidates", "{\"user_id\":" + user + ",\"scene\":\"hot\"}", 400);
        var dto = new QueryRecommendationDTO(2L, "　" + "😀".repeat(32) + "　", "😀".repeat(64), "", 1);
        assertThat(service.recommend(dto).scene()).isEqualTo("😀".repeat(32));
        assertError(() -> service.recommend(new QueryRecommendationDTO(2L, "😀".repeat(33), null, "", 1)), ErrorCode.SCENE_TOO_LONG);
        assertError(() -> service.recommend(new QueryRecommendationDTO(2L, "hot", "😀".repeat(65), "", 1)), ErrorCode.REQUEST_ID_TOO_LONG);
        mvc.perform(post("/api/feed-queries").header("Authorization", token()).contentType("application/json")
                .content("{\"scene\":\"recommend\",\"context\":{\"request_id\":12}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pageOf100HasBoundedSqlAndNoCountQueries() throws Exception {
        videos(110);
        jdbc.update("UPDATE video SET author_id=2 WHERE MOD(id,2)=0");
        sql.statements.clear();
        mvc.perform(get("/api/feed-items").param("scene", "recommend").param("limit", "100")
                .header("Authorization", token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(100));
        assertThat(sql.statements).hasSizeLessThanOrEqualTo(10);
        assertThat(sql.statements).noneMatch(statement -> statement.toLowerCase().contains("count("));
    }

    @Test
    void snapshotReadAndDatabaseFailuresAreNotDisguisedAsExpiry() {
        videos(2);
        var first = query("", 1);
        doThrow(new IllegalStateException("snapshot read")).when(cache).findSnapshot(anyString());
        assertError(() -> query(first.nextCursor(), 1), ErrorCode.LOAD_RECOMMENDATION_FAILED);
        doThrow(new DataAccessResourceFailureException("recall")).when(recommendations).recall(anyLong(), any(), anyInt());
        assertError(() -> query("", 1), ErrorCode.LOAD_RECOMMENDATION_FAILED);
    }

    @Test
    void interestSamplesRespectWindowOrderingAndLimit() {
        videos(1);
        LocalDateTime current = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        for (int index = 0; index < 205; index++) {
            jdbc.update("INSERT INTO video_view_events(user_id,video_id,scene,event_type,watch_ms,created_at) VALUES (2,1,'recommend','play',?,?)",
                    index, current.minusSeconds(205 - index));
        }
        jdbc.update("INSERT INTO video_view_events(user_id,video_id,scene,event_type,watch_ms,created_at) VALUES (2,1,'recommend','complete',999,?)", current.minusDays(30).minusSeconds(1));
        jdbc.update("INSERT INTO video_view_events(user_id,video_id,scene,event_type,watch_ms,created_at) VALUES (2,1,'recommend','skip',999,?)", current);
        var events = exposures.findInterestEvents(2L, current.minusDays(30), 200);
        assertThat(events).hasSize(200);
        assertThat(events.getFirst().getWatchMs()).isEqualTo(204);
        assertThat(events.getLast().getWatchMs()).isEqualTo(5);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfiguration {
        @Bean
        SqlRecorder recommendationSqlRecorder() { return new SqlRecorder(); }

    }

    /** 记录实际 JDBC prepare，既检查查询数量也检查分页插件没有生成 count。 */
    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    static class SqlRecorder implements Interceptor {
        final List<String> statements = new CopyOnWriteArrayList<>();
        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            statements.add(((StatementHandler) invocation.getTarget()).getBoundSql().getSql());
            return invocation.proceed();
        }

    }

    private org.springframework.test.web.servlet.ResultActions internal(String route, String body, int statusCode) throws Exception {
        return mvc.perform(post("/internal/" + route).header("X-Internal-Token", "gcfeed-dev-only-internal-token")
                .contentType("application/json").content(body)).andExpect(status().is(statusCode));
    }

    private String token() { return "Bearer " + jwt.signAccessToken(2L, "user"); }

    private io.github.gmjneko.gcfeed.modules.recommendation.vo.RecommendationPageVO query(String cursor, int limit) {
        return service.recommend(new QueryRecommendationDTO(2L, "recommend", "req", cursor, limit));
    }

    private void videos(int count) {
        for (long id = 1; id <= count; id++) video(id, 1);
    }

    private void video(long id, long author) {
        jdbc.update("INSERT INTO video(id,author_id,title,media_url,cover_url,status,published_at) VALUES (?,?,?,'/v.mp4','/v.jpg',2,?)",
                id, author, "video-" + id, LocalDateTime.ofInstant(TIME, ZoneOffset.UTC));
        jdbc.update("INSERT INTO video_stat(video_id) VALUES (?)", id);
    }

    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

}
