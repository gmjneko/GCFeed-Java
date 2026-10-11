package io.github.gmjneko.gcfeed.modules.video;

import io.github.gmjneko.gcfeed.modules.user.dto.RegisterDTO;
import io.github.gmjneko.gcfeed.modules.user.service.UserService;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 覆盖视频发布、幂等、公开性、作者列表和业务幂等删除。 */
@SpringBootTest
@AutoConfigureMockMvc
class VideoModuleTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private UserService userService;
    @Autowired
    private JwtUtil jwtUtil;
    @MockitoBean
    private RedissonClient redis;

    private final Map<String, RBucket<Object>> buckets = new ConcurrentHashMap<>();

    @BeforeEach
    void resetData() {
        jdbc.update("DELETE FROM video_stat");
        jdbc.update("DELETE FROM video");
        jdbc.update("DELETE FROM user_relation_stat");
        jdbc.update("DELETE FROM account");
        buckets.clear();
        when(redis.getBucket(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return buckets.computeIfAbsent(key, ignored -> {
                @SuppressWarnings("unchecked")
                RBucket<Object> bucket = mock(RBucket.class);
                when(bucket.isExists()).thenReturn(false);
                when(bucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);
                return bucket;
            });
        });
    }

    @Test
    void publishReplayListAndDeleteFollowContract() throws Exception {
        long authorId = userService.register(new RegisterDTO("video-author", "secret", "author")).id();
        long otherId = userService.register(new RegisterDTO("video-other", "secret", "other")).id();
        String authorToken = jwtUtil.signAccessToken(authorId, "user");
        String otherToken = jwtUtil.signAccessToken(otherId, "user");

        JsonNode created = data(mvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer " + authorToken)
                        .header("Idempotency-Key", "video-create-1")
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "title", "first video",
                                "description", "hello",
                                "media_url", "/uploads/video/first.mp4",
                                "cover_url", "/uploads/cover/first.jpg"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.author_id").value(authorId))
                .andExpect(jsonPath("$.data.status").value(2))
                .andExpect(jsonPath("$.data.like_count").value(0))
                .andReturn().getResponse().getContentAsString());
        long videoId = created.get("id").asLong();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM video_stat WHERE video_id = ?", Integer.class, videoId))
                .isEqualTo(1);

        JsonNode replayed = data(mvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer " + authorToken)
                        .header("Idempotency-Key", "video-create-1")
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "first video", "description", "hello",
                                "media_url", "/uploads/video/first.mp4", "cover_url", "/uploads/cover/first.jpg"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(replayed.get("id").asLong()).isEqualTo(videoId);
        assertThat(replayed.get("title").asText()).isEqualTo("first video");
        mvc.perform(post("/api/videos").header("Authorization", "Bearer " + authorToken)
                        .header("Idempotency-Key", "video-create-1").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "changed", "media_url", "changed", "cover_url", "changed"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(20010));


        mvc.perform(get("/api/videos/{id}", videoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(videoId));
        mvc.perform(get("/api/users/{id}/videos", authorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.has_more").value(false))
                .andExpect(jsonPath("$.data.next_cursor").isNotEmpty());
        mvc.perform(get("/api/users/me/videos")
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.has_more").value(false));

        jdbc.update("UPDATE video SET status = 3 WHERE id = ?", videoId);
        mvc.perform(get("/api/users/{id}/videos", authorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(get("/api/users/me/videos")
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value(3));

        mvc.perform(delete("/api/videos/{id}", videoId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/videos/{id}", videoId)
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mvc.perform(delete("/api/videos/{id}", videoId)
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/videos/{id}", videoId))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidVideoParametersAreRejected() throws Exception {
        long authorId = userService.register(new RegisterDTO("video-validation", "secret", "author")).id();
        String token = jwtUtil.signAccessToken(authorId, "user");
        mvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"   \",\"media_url\":\"m\",\"cover_url\":\"c\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(20003));
        mvc.perform(get("/api/videos/abc"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/{id}/videos?limit=0", authorId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(70001));
        mvc.perform(get("/api/users/{id}/videos?cursor=not-a-cursor", authorId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(70002));
    }

    @Test
    void authorVideosUseStableCursorPagination() throws Exception {
        long authorId = userService.register(new RegisterDTO("video-cursor", "secret", "author")).id();
        String token = jwtUtil.signAccessToken(authorId, "user");
        for (int i = 1; i <= 2; i++) {
            mvc.perform(post("/api/videos")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "cursor-video-" + i)
                            .contentType(APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "title", "cursor video " + i,
                                    "media_url", "/uploads/video/" + i + ".mp4",
                                    "cover_url", "/uploads/cover/" + i + ".jpg"))))
                    .andExpect(status().isCreated());
        }

        JsonNode firstPage = data(mvc.perform(get("/api/users/{id}/videos?limit=1", authorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.has_more").value(true))
                .andReturn().getResponse().getContentAsString());
        String cursor = firstPage.get("next_cursor").asText();
        mvc.perform(get("/api/users/{id}/videos?limit=1&cursor={cursor}", authorId, cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.has_more").value(false));
    }

    private JsonNode data(String body) {
        return json.readTree(body).get("data");
    }
}
