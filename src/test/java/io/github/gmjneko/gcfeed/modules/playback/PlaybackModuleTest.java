package io.github.gmjneko.gcfeed.modules.playback;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.jwt.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 真实 H2 验证播放参数回退、预加载顺序和 QoS 幂等；Redis 只替代令牌黑名单。 */
@SpringBootTest
@AutoConfigureMockMvc
class PlaybackModuleTest {

    private static final String TOKEN = "gcfeed-dev-only-internal-token";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtUtil jwt;
    @Autowired
    private JsonMapper json;
    @MockitoBean
    private RedissonClient redis;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void resetData() {
        for (String table : java.util.List.of("playback_qos_log", "playback_config", "video_stat", "video", "account")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO account(id, account, password, nickname) VALUES (1, 'playback-user', 'unused', 'viewer')");
        RBucket<Object> marker = mock(RBucket.class);
        when(redis.getBucket(anyString())).thenReturn(marker);
        when(marker.isExists()).thenReturn(false);
        when(marker.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);
    }

    @Test
    void configFallsBackClampsAndKeepsPlatformCase() throws Exception {
        JsonNode defaults = data(auth(get("/api/playback-config")), 200);
        assertThat(defaults.get("id").asLong()).isZero();
        assertThat(defaults.get("platform").asText()).isEqualTo("Web");
        assertThat(defaults.get("network_type").asText()).isEqualTo("DEFAULT");
        assertThat(defaults.get("preload_count").asInt()).isEqualTo(3);
        assertThat(defaults.get("buffer_ms").asInt()).isEqualTo(1200);

        jdbc.update("INSERT INTO playback_config(platform, network_type, preload_count, buffer_ms) VALUES ('Web', 'DEFAULT', 3, 1200)");
        jdbc.update("INSERT INTO playback_config(platform, network_type, preload_count, buffer_ms) VALUES ('Web', 'WiFi', 20, 0)");
        jdbc.update("INSERT INTO playback_config(platform, network_type, preload_count, buffer_ms) VALUES ('Web', '4G', 2, 1500)");

        JsonNode wifi = data(auth(get("/api/playback-config").param("platform", " Web ").param("network_type", "wi-fi")), 200);
        assertThat(wifi.get("platform").asText()).isEqualTo("Web");
        assertThat(wifi.get("network_type").asText()).isEqualTo("WiFi");
        assertThat(wifi.get("preload_count").asInt()).isEqualTo(10);
        assertThat(wifi.get("buffer_ms").asInt()).isEqualTo(1200);

        JsonNode fifth = data(auth(get("/api/playback-config").param("platform", "Web").param("network_type", "5g")), 200);
        assertThat(fifth.get("network_type").asText()).isEqualTo("DEFAULT");
        assertThat(fifth.get("preload_count").asInt()).isEqualTo(3);
        assertThat(fifth.get("buffer_ms").asInt()).isEqualTo(1200);

        JsonNode lower = data(auth(get("/api/playback-config").param("platform", "web").param("network_type", "WiFi")), 200);
        assertThat(lower.get("id").asLong()).isZero();
        assertThat(lower.get("platform").asText()).isEqualTo("web");
        assertThat(lower.get("preload_count").asInt()).isEqualTo(3);

        mvc.perform(auth(get("/api/playback-config").param("platform", "p".repeat(17))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PLATFORM.getCode()));
        mvc.perform(get("/api/playback-config")).andExpect(status().isUnauthorized());
    }

    @Test
    void preloadReturnsEarlierPublishedVideosAndFallsBackWhenCurrentIsMissing() throws Exception {
        insertVideo(1, 2, "2026-10-03 10:00:00", "/media/new.mp4", "/cover/new.jpg");
        insertVideo(2, 2, "2026-10-02 10:00:00", "/media/mid.mp4", "/cover/mid.jpg");
        insertVideo(3, 2, "2026-10-01 10:00:00", "/media/old.mp4", "/cover/old.jpg");
        insertVideo(4, 3, "2026-10-04 10:00:00", "/media/offline.mp4", "/cover/offline.jpg");

        JsonNode earlier = data(auth(get("/api/preload-videos").param("current_video_id", "1").param("limit", "2")), 200);
        assertThat(earlier.get("items")).hasSize(2);
        assertThat(earlier.get("items").get(0).get("video_id").asLong()).isEqualTo(2L);
        assertThat(earlier.get("items").get(0).get("media_url").asText()).isEqualTo("/media/mid.mp4");
        assertThat(earlier.get("items").get(1).get("video_id").asLong()).isEqualTo(3L);

        JsonNode latest = data(auth(get("/api/preload-videos").param("current_video_id", "0").param("limit", "2")), 200);
        assertThat(latest.get("items").get(0).get("video_id").asLong()).isEqualTo(1L);
        assertThat(latest.get("items").get(1).get("video_id").asLong()).isEqualTo(2L);

        JsonNode missing = data(auth(get("/api/preload-videos").param("current_video_id", "4").param("limit", "10")), 200);
        assertThat(missing.get("items")).hasSize(3);
        assertThat(missing.get("items").get(0).get("video_id").asLong()).isEqualTo(1L);

        mvc.perform(auth(get("/api/preload-videos").param("current_video_id", "-1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(ErrorCode.INVALID_VIDEO_ID.getCode()));
        mvc.perform(auth(get("/api/preload-videos").param("limit", "0")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(ErrorCode.INVALID_LIMIT.getCode()));
    }

    @Test
    void qosReplayKeepsTheFirstReportAndAllowsAnonymousInternalReports() throws Exception {
        String body = """
                {"video_id":9,"first_frame_ms":186,"stutter_count":1,"watch_ms":900,"user_id":99}
                """;
        JsonNode created = data(auth(post("/api/playback-qos-reports").contentType("application/json")
                .header("Idempotency-Key", "qos-1").content(body)), 201);
        assertThat(created.get("user_id").asLong()).isEqualTo(1L);
        assertThat(created.get("first_frame_ms").asInt()).isEqualTo(186);
        assertThat(created.get("video_id").asLong()).isEqualTo(9L);

        JsonNode replay = data(auth(post("/api/playback-qos-reports").contentType("application/json")
                .header("Idempotency-Key", "qos-1")
                .content("{\"video_id\":9,\"first_frame_ms\":999,\"stutter_count\":8,\"watch_ms\":1}")), 200);
        assertThat(replay.get("id").asLong()).isEqualTo(created.get("id").asLong());
        assertThat(replay.get("first_frame_ms").asInt()).isEqualTo(186);
        assertThat(replay.get("stutter_count").asInt()).isEqualTo(1);
        assertThat(replay.get("created_at").asText()).isEqualTo(created.get("created_at").asText());
        assertThat(count("playback_qos_log")).isEqualTo(1);

        data(auth(post("/api/playback-qos-reports").contentType("application/json")
                .content("{\"video_id\":9,\"watch_ms\":10}")), 201);
        data(auth(post("/api/playback-qos-reports").contentType("application/json")
                .header("Idempotency-Key", "  ")
                .content("{\"video_id\":9,\"watch_ms\":11}")), 201);
        assertThat(count("playback_qos_log")).isEqualTo(3);

        JsonNode internal = data(post("/internal/playback-qos-reports").header("X-Internal-Token", TOKEN)
                .contentType("application/json")
                .content("{\"user_id\":0,\"video_id\":9,\"stutter_count\":2}"), 201);
        assertThat(internal.get("user_id").asLong()).isZero();
        assertThat(internal.get("stutter_count").asInt()).isEqualTo(2);
        assertThat(internal.get("first_frame_ms").isNull()).isTrue();

        mvc.perform(auth(post("/api/playback-qos-reports").contentType("application/json")
                        .content("{\"video_id\":9,\"watch_ms\":-1}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(ErrorCode.INVALID_WATCH_MS.getCode()));
        mvc.perform(post("/internal/playback-qos-reports").header("X-Internal-Token", TOKEN)
                        .contentType("application/json")
                        .content("{\"user_id\":-1,\"video_id\":9}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(ErrorCode.INVALID_USER_ID.getCode()));
        mvc.perform(auth(post("/api/playback-qos-reports").contentType("application/json")
                        .header("Idempotency-Key", "k".repeat(129))
                        .content("{\"video_id\":9,\"watch_ms\":1}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PLAYBACK_IDEMPOTENCY_KEY_TOO_LONG.getCode()));
        mvc.perform(post("/api/playback-qos-reports").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/playback-qos-reports").header("X-Internal-Token", "wrong")
                        .contentType("application/json").content("{\"user_id\":0,\"video_id\":9}"))
                .andExpect(status().isUnauthorized());
    }

    private void insertVideo(long id, int status, String publishedAt, String media, String cover) {
        jdbc.update("""
                INSERT INTO video(id, author_id, title, media_url, cover_url, status, published_at)
                VALUES (?, 1, 'title', ?, ?, ?, ?)
                """, id, media, cover, status, publishedAt);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwt.signAccessToken(1L, "user"));
    }

    private JsonNode data(MockHttpServletRequestBuilder request, int status) throws Exception {
        MvcResult result = mvc.perform(request).andExpect(status().is(status)).andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
