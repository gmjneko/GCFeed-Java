package io.github.gmjneko.gcfeed.modules.exposure;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.common.jwt.JwtUtil;
import io.github.gmjneko.gcfeed.modules.exposure.dto.CreateViewEventDTO;
import io.github.gmjneko.gcfeed.modules.exposure.entity.Exposure;
import io.github.gmjneko.gcfeed.modules.exposure.entity.ViewEvent;
import io.github.gmjneko.gcfeed.modules.exposure.repository.ExposureRepository;
import io.github.gmjneko.gcfeed.modules.exposure.service.ExposureService;
import io.github.gmjneko.gcfeed.modules.exposure.vo.RecordViewEventVO;
import io.github.gmjneko.gcfeed.modules.video.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 使用真实 H2 Mapper 和 Spring 事务验证观看上报、并发计数及失败回滚；Redis 仅替代鉴权存储。 */
@SpringBootTest
@AutoConfigureMockMvc
class ExposureModuleTest {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final LocalDateTime OLD_TIME = LocalDateTime.of(2020, 1, 1, 12, 0, 0);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtUtil jwt;
    @Autowired
    private JsonMapper json;
    @Autowired
    private ExposureService service;
    @MockitoBean
    private RedissonClient redis;
    @MockitoSpyBean
    private ExposureRepository repository;
    @MockitoSpyBean
    private VideoRepository videoRepository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void resetData() {
        for (String table : List.of("video_view_events", "exposures", "video_embedding", "video_stat", "video",
                "user_relation_stat", "account")) {
            jdbc.update("DELETE FROM " + table);
        }
        for (long id = 1; id <= 4; id++) {
            jdbc.update("INSERT INTO account(id, account, password, nickname) VALUES (?, ?, 'unused', 'viewer')",
                    id, "exposure-user-" + id);
        }
        for (long id = 1; id <= 2; id++) {
            jdbc.update("""
                    INSERT INTO video(id, author_id, title, media_url, cover_url, status, published_at)
                    VALUES (?, 1, 'title', 'media', 'cover', 2, CURRENT_TIMESTAMP)
                    """, id);
        }
        RBucket<Object> marker = mock(RBucket.class);
        when(redis.getBucket(anyString())).thenReturn(marker);
        when(marker.isExists()).thenReturn(false);
    }

    @Test
    void firstExposureReturnsCreatedEventAndActualAggregate() throws Exception {
        JsonNode result = created("""
                {"video_id":1,"scene":"timeline","request_id":"req-1","event_type":"exposed"}
                """);
        JsonNode event = result.get("event");
        JsonNode exposure = result.get("exposure");
        assertThat(event.get("id").asLong()).isPositive();
        assertThat(event.get("user_id").asLong()).isEqualTo(2);
        assertThat(event.get("video_id").asLong()).isEqualTo(1);
        assertThat(event.get("scene").asText()).isEqualTo("timeline");
        assertThat(event.get("request_id").asText()).isEqualTo("req-1");
        assertThat(event.get("event_type").asText()).isEqualTo("exposed");
        assertThat(event.get("watch_ms").asInt()).isZero();
        assertThat(event.get("completed").asBoolean()).isFalse();
        LocalDateTime createdAt = LocalDateTime.parse(event.get("created_at").asText(), DATE_TIME);
        assertThat(createdAt.getNano()).isZero();
        assertThat(jdbc.queryForObject("SELECT created_at FROM video_view_events", LocalDateTime.class))
                .isEqualTo(createdAt);
        assertThat(exposure.get("user_id").asLong()).isEqualTo(2);
        assertThat(exposure.get("video_id").asLong()).isEqualTo(1);
        assertThat(exposure.get("exposure_count").asInt()).isEqualTo(1);
        assertThat(exposure.get("first_exposed_at").asText()).isEqualTo(event.get("created_at").asText());
        assertThat(exposure.get("last_exposed_at").asText()).isEqualTo(event.get("created_at").asText());
        assertThat(exposure.get("last_scene").asText()).isEqualTo("timeline");
        assertThat(exposure.has("id")).isFalse();
        assertThat(exposure.has("created_at")).isFalse();
        assertThat(count("video_view_events")).isEqualTo(1);
        assertThat(count("exposures")).isEqualTo(1);
    }

    @Test
    void playCompleteAndSkipOnlyAppendEventsAndPreserveExistingExposure() throws Exception {
        created("""
                {"video_id":1,"scene":"timeline","event_type":"exposed"}
                """);
        for (String eventType : List.of("play", "complete", "skip")) {
            JsonNode result = created(json.writeValueAsString(Map.of("video_id", 1, "scene", "recommend",
                    "event_type", eventType, "watch_ms", 12000, "completed", false)));
            assertThat(result.has("exposure")).isFalse();
            assertThat(result.get("event").get("event_type").asText()).isEqualTo(eventType);
            assertThat(result.get("event").get("completed").asBoolean()).isEqualTo(eventType.equals("complete"));
            assertThat(result.get("event").get("watch_ms").asInt()).isEqualTo(12000);
        }
        Exposure exposure = repository.findByUserAndVideo(2L, 1L).orElseThrow();
        assertThat(exposure.getExposureCount()).isEqualTo(1);
        assertThat(exposure.getLastScene()).isEqualTo("timeline");
        assertThat(count("video_view_events")).isEqualTo(4);
        assertThat(count("exposures")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT completed FROM video_view_events WHERE event_type='complete'",
                Boolean.class)).isTrue();
    }

    @Test
    void optionalNullsUseDefaultsAndExplicitCompletedIsPreserved() throws Exception {
        JsonNode result = created("""
                {"video_id":1,"scene":"timeline","event_type":"play", "watch_ms":null,"completed":null}
                """);
        assertThat(result.get("event").get("watch_ms").asInt()).isZero();
        assertThat(result.get("event").get("completed").asBoolean()).isFalse();
        assertThat(result.get("event").has("request_id")).isFalse();
        assertThat(result.has("exposure")).isFalse();
        JsonNode completed = created("""
                {"video_id":1,"scene":"timeline","event_type":"play","completed":true}
                """);
        assertThat(completed.get("event").get("completed").asBoolean()).isTrue();
        assertThat(count("exposures")).isZero();
    }

    @Test
    void sceneAndEventTypeNormalizeAfterUnicodeWhitespaceAndUseFixedLocale() throws Exception {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            for (String scene : List.of("TIMELINE", "HOT", "RECOMMEND", "CustomSignal")) {
                JsonNode result = created(json.writeValueAsString(Map.of("video_id", 1, "scene", "　 " + scene + " 　",
                        "event_type", "　 ExPoSeD 　", "request_id", "　 Req-Case 　")));
                assertThat(result.get("event").get("scene").asText()).isEqualTo(scene.toLowerCase(Locale.ROOT));
                assertThat(result.get("event").get("event_type").asText()).isEqualTo("exposed");
                assertThat(result.get("event").get("request_id").asText()).isEqualTo("Req-Case");
            }
        } finally {
            Locale.setDefault(original);
        }
        assertThat(count("video_view_events")).isEqualTo(4);
        assertThat(repository.findByUserAndVideo(2L, 1L).orElseThrow().getExposureCount()).isEqualTo(4);
    }

    @Test
    void lengthLimitsCountUnicodeCharactersAfterNormalization() throws Exception {
        for (String unit : List.of("x", "中", "😀")) {
            String scene = unit.repeat(32);
            String requestId = unit.repeat(64);
            JsonNode result = created(json.writeValueAsString(Map.of("video_id", 1, "scene", "　" + scene + "　",
                    "request_id", " " + requestId + " ", "event_type", "exposed")));
            assertThat(result.get("event").get("scene").asText()).isEqualTo(scene);
            assertThat(result.get("event").get("request_id").asText()).isEqualTo(requestId);
            assertThat(result.get("exposure").get("last_scene").asText()).isEqualTo(scene);
            invalid(json.writeValueAsString(Map.of("video_id", 1, "scene", unit.repeat(33), "event_type", "play")),
                    ErrorCode.SCENE_TOO_LONG);
            invalid(json.writeValueAsString(Map.of("video_id", 1, "scene", "timeline", "request_id", unit.repeat(65),
                    "event_type", "play")), ErrorCode.REQUEST_ID_TOO_LONG);
        }
        assertThat(count("video_view_events")).isEqualTo(3);
        assertThat(repository.findByUserAndVideo(2L, 1L).orElseThrow().getExposureCount()).isEqualTo(3);
    }

    @Test
    void emptyRequestIdsAreNullInStorageAndAbsentFromResponses() throws Exception {
        for (String requestId : List.of("", "　 \t ")) {
            JsonNode result = created(json.writeValueAsString(Map.of("video_id", 1, "scene", "timeline",
                    "event_type", "play", "request_id", requestId)));
            assertThat(result.get("event").has("request_id")).isFalse();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM video_view_events WHERE request_id IS NULL",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void invalidParametersDoNotWriteEitherTable() throws Exception {
        for (String videoId : List.of("null", "0", "-1")) {
            invalid("{\"video_id\":" + videoId + ",\"scene\":\"timeline\",\"event_type\":\"play\"}",
                    ErrorCode.INVALID_VIDEO_ID);
        }
        invalid("{}", ErrorCode.INVALID_VIDEO_ID);
        for (String scene : List.of("", "　 ")) {
            invalid(json.writeValueAsString(Map.of("video_id", 1, "scene", scene, "event_type", "play")),
                    ErrorCode.EMPTY_SCENE);
        }
        invalid("{\"video_id\":1,\"event_type\":\"play\"}", ErrorCode.EMPTY_SCENE);
        for (String eventType : List.of("", "　 ", "unknown", "ｐｌａｙ")) {
            invalid(json.writeValueAsString(Map.of("video_id", 1, "scene", "timeline", "event_type", eventType)),
                    ErrorCode.INVALID_EVENT_TYPE);
        }
        invalid("{\"video_id\":1,\"scene\":\"timeline\"}", ErrorCode.INVALID_EVENT_TYPE);
        invalid("{\"video_id\":1,\"scene\":\"timeline\",\"event_type\":\"play\",\"watch_ms\":-1}",
                ErrorCode.INVALID_WATCH_MS);
        for (String body : List.of("", "null", "{", "{\"video_id\":\"bad\"}",
                "{\"video_id\":1,\"scene\":\"timeline\",\"event_type\":\"play\",\"watch_ms\":2147483648}")) {
            invalid(body, ErrorCode.PARAM_ERROR);
        }
        assertThat(count("video_view_events")).isZero();
        assertThat(count("exposures")).isZero();
    }

    @Test
    void authenticationControlsIdentityEvenWhenRequestBodySuppliesAnotherUser() throws Exception {
        String body = "{\"video_id\":1,\"scene\":\"timeline\",\"event_type\":\"exposed\",\"user_id\":3}";
        mvc.perform(report(body)).andExpect(status().isUnauthorized());
        mvc.perform(report(body).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        assertThat(count("video_view_events")).isZero();
        JsonNode result = created(body);
        assertThat(result.get("event").get("user_id").asLong()).isEqualTo(2);
        assertThat(result.get("exposure").get("user_id").asLong()).isEqualTo(2);
        assertThat(repository.findByUserAndVideo(3L, 1L)).isEmpty();
    }

    @Test
    void missingAndUnpublishedVideosReturnNotFoundWithoutWrites() throws Exception {
        String body = "{\"video_id\":1,\"scene\":\"timeline\",\"event_type\":\"exposed\"}";
        for (int state : List.of(1, 3, 4)) {
            jdbc.update("UPDATE video SET status = ? WHERE id = 1", state);
            mvc.perform(auth(report(body))).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VIDEO_NOT_FOUND.getCode()));
        }
        mvc.perform(auth(report("{\"video_id\":99999,\"scene\":\"timeline\",\"event_type\":\"play\"}")))
                .andExpect(status().isNotFound());
        assertThat(count("video_view_events")).isZero();
        assertThat(count("exposures")).isZero();
    }

    @Test
    void duplicateRequestIdsAndIdempotencyHeadersStillRecordEveryExposure() throws Exception {
        String body = "{\"video_id\":1,\"scene\":\"timeline\",\"event_type\":\"exposed\",\"request_id\":\"same\"}";
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String response = mvc.perform(auth(report(body)).header("Idempotency-Key", "same"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            JsonNode result = json.readTree(response).get("data");
            ids.add(result.get("event").get("id").asLong());
            assertThat(result.get("exposure").get("exposure_count").asInt()).isEqualTo(i + 1);
        }
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(count("video_view_events")).isEqualTo(3);
        assertThat(count("exposures")).isEqualTo(1);
    }

    @Test
    void repeatedExposurePreservesFirstTimeAndRefreshesLastFacts() throws Exception {
        service.recordViewEvent(2L, event(1L, "timeline"));
        jdbc.update("""
                UPDATE exposures SET first_exposed_at=?, last_exposed_at=?, created_at=?, updated_at=?
                WHERE user_id=2 AND video_id=1
                """, OLD_TIME, OLD_TIME, OLD_TIME, OLD_TIME);
        Exposure before = repository.findByUserAndVideo(2L, 1L).orElseThrow();
        RecordViewEventVO result = service.recordViewEvent(2L, event(1L, "hot"));
        Exposure after = repository.findByUserAndVideo(2L, 1L).orElseThrow();
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getCreatedAt()).isEqualTo(OLD_TIME);
        assertThat(after.getFirstExposedAt()).isEqualTo(OLD_TIME);
        assertThat(after.getLastExposedAt()).isEqualTo(result.event().createdAt()).isAfter(OLD_TIME);
        assertThat(after.getUpdatedAt()).isAfter(OLD_TIME);
        assertThat(after.getLastScene()).isEqualTo("hot");
        assertThat(after.getExposureCount()).isEqualTo(2);
        assertThat(result.exposure().firstExposedAt()).isEqualTo(OLD_TIME);
        assertThat(result.exposure().exposureCount()).isEqualTo(2);
    }

    @Test
    void concurrentExposuresDoNotLoseIncrementsOrReturnStaleCounts() throws Exception {
        List<RecordViewEventVO> results = parallel(8, i -> service.recordViewEvent(2L, event(1L, "recommend")));
        assertThat(results.stream().map(result -> result.exposure().exposureCount()).sorted().toList())
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(results.stream().map(result -> result.event().id()).toList()).doesNotHaveDuplicates();
        assertThat(count("video_view_events")).isEqualTo(8);
        assertThat(count("exposures")).isEqualTo(1);
        assertThat(repository.findByUserAndVideo(2L, 1L).orElseThrow().getExposureCount()).isEqualTo(8);
    }

    @Test
    void concurrentDifferentUsersAndVideosHaveIndependentAggregates() throws Exception {
        parallel(8, i -> service.recordViewEvent(2L + i % 2, event(1L + (i / 2) % 2, "timeline")));
        for (long userId : List.of(2L, 3L)) {
            for (long videoId : List.of(1L, 2L)) {
                assertThat(repository.findByUserAndVideo(userId, videoId).orElseThrow().getExposureCount()).isEqualTo(2);
            }
        }
        assertThat(count("video_view_events")).isEqualTo(8);
        assertThat(count("exposures")).isEqualTo(4);
    }

    @Test
    void aggregationFailureReturnsServerErrorAndRollsBackEvent() throws Exception {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("injected db failure");
        doThrow(failure).when(repository).upsertExposure(any(Exposure.class));
        assertThatThrownBy(() -> service.recordViewEvent(2L, event(1L, "timeline")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SAVE_VIEW_EVENT_FAILED))
                .hasCause(failure);
        mvc.perform(auth(report("{\"video_id\":1,\"scene\":\"timeline\",\"event_type\":\"exposed\"}")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(70012));
        assertThat(count("video_view_events")).isZero();
        assertThat(count("exposures")).isZero();
    }

    @Test
    void aggregationReadFailureRollsBackNewRows() {
        doThrow(new DataAccessResourceFailureException("read failed"))
                .when(repository).findByUserAndVideo(2L, 1L);
        saveFailure(() -> service.recordViewEvent(2L, event(1L, "timeline")));
        assertThat(count("video_view_events")).isZero();
        assertThat(count("exposures")).isZero();
    }

    @Test
    void emptyReadbackRollsBackNewRows() {
        doReturn(Optional.empty()).when(repository).findByUserAndVideo(2L, 1L);
        saveFailure(() -> service.recordViewEvent(2L, event(1L, "timeline")));
        assertThat(count("video_view_events")).isZero();
        assertThat(count("exposures")).isZero();
    }

    @Test
    void failedReadbackRestoresExistingAggregateAndRemovesNewEvent() {
        service.recordViewEvent(2L, event(1L, "timeline"));
        jdbc.update("UPDATE exposures SET last_exposed_at=?, updated_at=? WHERE user_id=2 AND video_id=1",
                OLD_TIME, OLD_TIME);
        Map<String, Object> before = jdbc.queryForMap("SELECT * FROM exposures WHERE user_id=2 AND video_id=1");
        doReturn(Optional.empty()).when(repository).findByUserAndVideo(2L, 1L);
        saveFailure(() -> service.recordViewEvent(2L, event(1L, "hot")));
        assertThat(jdbc.queryForMap("SELECT * FROM exposures WHERE user_id=2 AND video_id=1")).isEqualTo(before);
        assertThat(count("video_view_events")).isEqualTo(1);
        assertThat(count("exposures")).isEqualTo(1);
    }

    @Test
    void unsuccessfulInsertRollsBackWithoutReturningSuccess() {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            return 0;
        }).when(repository).insertViewEvent(any(ViewEvent.class));
        saveFailure(() -> service.recordViewEvent(2L, event(1L, "timeline")));
        assertThat(count("video_view_events")).isZero();
        assertThat(count("exposures")).isZero();
    }

    @Test
    void unsuccessfulUpsertRollsBackItsWriteAndTheEvent() {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            return 0;
        }).when(repository).upsertExposure(any(Exposure.class));
        saveFailure(() -> service.recordViewEvent(2L, event(1L, "timeline")));
        assertThat(count("video_view_events")).isZero();
        assertThat(count("exposures")).isZero();
    }

    @Test
    void videoDatabaseFailureUsesSaveErrorAndRetainsCause() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("video db failed");
        doThrow(failure).when(videoRepository).existsPublished(1L);
        assertThatThrownBy(() -> service.recordViewEvent(2L, event(1L, "timeline")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SAVE_VIEW_EVENT_FAILED))
                .hasCause(failure);
        assertThat(count("video_view_events")).isZero();
        assertThat(count("exposures")).isZero();
    }

    private CreateViewEventDTO event(Long videoId, String scene) {
        return new CreateViewEventDTO(videoId, scene, null, "exposed", null, null);
    }

    private JsonNode created(String body) throws Exception {
        String response = mvc.perform(auth(report(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("data");
    }

    private MockHttpServletRequestBuilder report(String body) {
        return post("/api/video-view-events").contentType("application/json").content(body);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwt.signAccessToken(2L, "user"));
    }

    private void invalid(String body, ErrorCode errorCode) throws Exception {
        mvc.perform(auth(report(body))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(errorCode.getCode()));
    }

    private void saveFailure(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SAVE_VIEW_EVENT_FAILED));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    /** 同时释放所有线程，放大首次插入冲突与重复更新竞争；Future 失败必须传回测试线程。 */
    private List<RecordViewEventVO> parallel(int threads, IntFunction<RecordViewEventVO> work) throws Exception {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(threads)) {
            List<Future<RecordViewEventVO>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("并发测试未按时启动");
                    }
                    return work.apply(index);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<RecordViewEventVO> results = new ArrayList<>();
            for (Future<RecordViewEventVO> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        }
    }

}
