package io.github.gmjneko.gcfeed.modules.social;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.common.jwt.JwtUtil;
import io.github.gmjneko.gcfeed.modules.interaction.repository.CommentRepository;
import io.github.gmjneko.gcfeed.modules.interaction.service.CommentService;
import io.github.gmjneko.gcfeed.modules.relation.repository.RelationRepository;
import io.github.gmjneko.gcfeed.modules.relation.service.RelationService;
import org.junit.jupiter.api.*;
import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntConsumer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SocialModuleTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired JwtUtil jwt;
    @Autowired JsonMapper json;
    @Autowired CommentService comments;
    @Autowired RelationService relations;
    @MockitoBean RedissonClient redis;
    @MockitoSpyBean CommentRepository commentRepository;
    @MockitoSpyBean RelationRepository relationRepository;
    RBucket<Object> bucket;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        for (String table : List.of("interaction_comment", "user_follow", "interaction_action", "video_stat", "video", "user_relation_stat", "account")) {
            db.update("DELETE FROM " + table);
        }
        for (int id = 1; id <= 12; id++) {
            db.update("INSERT INTO account(id,account,password,nickname) VALUES (?,?,'unused',?)", id, "social-" + id, "user-" + id);
            db.update("INSERT INTO user_relation_stat(user_id) VALUES (?)", id);
        }
        for (int id = 1; id <= 4; id++) {
            db.update("INSERT INTO video(id,author_id,title,media_url,cover_url,published_at) VALUES (?,?,'title','media','cover','2026-10-01 12:00:00')", id, id <= 2 ? 1 : 2);
            db.update("INSERT INTO video_stat(video_id) VALUES (?)", id);
        }
        bucket = mock(RBucket.class);
        when(redis.getBucket(anyString())).thenReturn(bucket);
        when(bucket.isExists()).thenReturn(false);
        when(bucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);
    }

    @Test
    void commentHttpContractAndDurableKeys() throws Exception {
        var request = post("/api/videos/1/comments").contentType("application/json").content("{\"content\":\" hello \"}")
                .header("Idempotency-Key", "durable");
        JsonNode first = data(auth(request, 2), 201);
        assertThat(first.get("content").asText()).isEqualTo("hello");
        assertThat(first.get("user_nickname").asText()).isEqualTo("user-2");
        assertThat(first.get("comment_count").asInt()).isEqualTo(1);
        assertThat(data(auth(request, 2), 200).get("id").asLong()).isEqualTo(first.get("id").asLong());
        conflict(() -> comments.create(2L, 1L, "different", "durable"));
        conflict(() -> comments.create(2L, 2L, "hello", "durable"));
        assertThat(count("interaction_comment")).isEqualTo(1);
        long id = first.get("id").asLong();
        data(auth(delete("/api/comments/" + id), 2), 200);
        conflict(() -> comments.create(2L, 1L, "hello", "durable"));
        assertThat(comments.list(1L, "", 20).items()).isEmpty();
        assertThat(comments.create(2L, 1L, "same", null).created()).isTrue();
        assertThat(comments.create(2L, 1L, "same", " ").created()).isTrue();
    }

    @Test
    void commentValidationVisibilityAndAuthentication() throws Exception {
        mvc.perform(post("/api/videos/1/comments").contentType("application/json").content("{\"content\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        for (String content : List.of("", "   ", "x".repeat(1001))) {
            mvc.perform(auth(post("/api/videos/1/comments").contentType("application/json")
                    .content(json.writeValueAsString(Map.of("content", content))), 2)).andExpect(status().isBadRequest());
        }
        assertThat(comments.create(2L, 1L, "😀".repeat(1000), null).comment().content()).hasSize(2000);
        mvc.perform(auth(post("/api/videos/1/comments").contentType("application/json")
                .content("{}").header("Idempotency-Key", "x".repeat(129)), 2)).andExpect(status().isBadRequest());
        db.update("UPDATE video SET status=3 WHERE id=1");
        mvc.perform(get("/api/videos/1/comments")).andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/videos/1/comments").contentType("application/json").content("{\"content\":\"x\"}"), 2))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/videos/2/comments").param("cursor", "bad")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/videos/2/comments").param("limit", "0")).andExpect(status().isBadRequest());
        mvc.perform(auth(delete("/api/comments/99999"), 2)).andExpect(status().isNotFound());
    }

    @Test
    void commentPaginationIsStableAndDeletedUsersHaveNoPrivateFields() throws Exception {
        for (int i = 0; i < 5; i++) comments.create(2L, 1L, "comment-" + i, null);
        db.update("UPDATE interaction_comment SET created_at='2026-10-01 12:00:00'");
        var first = comments.list(1L, "", 2);
        var second = comments.list(1L, first.nextCursor(), 2);
        var third = comments.list(1L, second.nextCursor(), 2);
        Set<Long> ids = new HashSet<>();
        for (var page : List.of(first, second, third)) page.items().forEach(c -> assertThat(ids.add(c.id())).isTrue());
        assertThat(ids).hasSize(5);
        assertThat(third.hasMore()).isFalse();
        db.update("UPDATE account SET status=3 WHERE id=2");
        JsonNode item = data(get("/api/videos/1/comments"), 200).get("items").get(0);
        assertThat(item.get("user_nickname").isNull()).isTrue();
        assertThat(item.has("comment_count")).isFalse();
        assertThat(item.has("password")).isFalse();
    }

    @Test
    void deletionChecksAllRolesAndAllowsOfflineVideo() throws Exception {
        long id = comments.create(2L, 1L, "first", null).comment().id();
        mvc.perform(auth(delete("/api/comments/" + id), 3)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/comments/" + id)).andExpect(status().isUnauthorized());
        db.update("UPDATE video SET status=3 WHERE id=1");
        assertThat(data(auth(delete("/api/comments/" + id), 1), 200).get("comment_count").asInt()).isZero();
        assertThat(comments.delete(2L, "user", id).commentCount()).isZero();
        mvc.perform(auth(delete("/api/comments/" + id), 3)).andExpect(status().isForbidden());
        long adminId = comments.create(2L, 2L, "second", null).comment().id();
        mvc.perform(delete("/api/comments/" + adminId).header("Authorization", "Bearer " + jwt.signAccessToken(3L, "admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.comment_count").value(0));
    }

    @Test
    void concurrentCommentCreateAndDeleteCountOnce() throws Exception {
        parallel(8, i -> comments.create(2L, 1L, "same", "concurrent"));
        assertThat(count("interaction_comment")).isEqualTo(1);
        long id = db.queryForObject("SELECT id FROM interaction_comment", Long.class);
        parallel(8, i -> comments.delete(2L, "user", id));
        assertThat(commentCount(1)).isZero();
        parallel(8, i -> comments.create((long) i + 2, 1L, "different users", null));
        assertThat(commentCount(1)).isEqualTo(8);
    }

    @Test
    void crossVideoCommentKeyRaceCreatesOnlyOneAndReportsConflict() throws Exception {
        parallel(8, i -> {
            try { comments.create(2L, i % 2 == 0 ? 1L : 2L, "same", "cross-video"); }
            catch (BusinessException e) { assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMENT_REQUEST_CONFLICT); }
        });
        assertThat(count("interaction_comment")).isEqualTo(1);
        assertThat(commentCount(1) + commentCount(2)).isEqualTo(1);
    }

    @Test
    void commentFailureRollsBackBothFactsAndCounters() {
        doThrow(new IllegalStateException("injected")).when(commentRepository).changeCount(1L, 1);
        assertThatThrownBy(() -> comments.create(2L, 1L, "rollback", null)).isInstanceOf(IllegalStateException.class);
        assertThat(count("interaction_comment")).isZero();
        doCallRealMethod().when(commentRepository).changeCount(1L, 1);
        long id = comments.create(2L, 1L, "delete rollback", null).comment().id();
        doThrow(new IllegalStateException("injected")).when(commentRepository).changeCount(1L, -1);
        assertThatThrownBy(() -> comments.delete(2L, "user", id)).isInstanceOf(IllegalStateException.class);
        assertThat(comments.list(1L, "", 20).items()).hasSize(1);
        assertThat(commentCount(1)).isEqualTo(1);
    }

    @Test
    void shortRequestWindowRejectsDuplicatesBeforeBusinessLogic() throws Exception {
        when(bucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(false);
        mvc.perform(auth(post("/api/videos/1/comments").contentType("application/json").content("{\"content\":\"x\"}")
                .header("Idempotency-Key", "same"), 2)).andExpect(status().isConflict());
        mvc.perform(auth(put("/api/users/me/following/1").header("Idempotency-Key", "same"), 2))
                .andExpect(status().isConflict());
        assertThat(count("interaction_comment")).isZero();
        assertThat(count("user_follow")).isZero();
        verify(redis).getBucket(contains("comment.create"));
        verify(redis).getBucket(contains("relation.follow"));
    }

    @Test
    void followingContractCountersAndProfileStayConsistent() throws Exception {
        assertThat(relations.unfollow(2L, 1L, null).followingCount()).isZero();
        assertThat(count("user_follow")).isZero();
        for (int i = 0; i < 2; i++) {
            JsonNode result = data(auth(put("/api/users/me/following/1"), 2), 200);
            assertThat(result.get("following").asBoolean()).isTrue();
            assertThat(result.get("following_count").asInt()).isEqualTo(1);
            assertThat(result.get("follower_count").asInt()).isEqualTo(1);
        }
        assertThat(data(auth(get("/api/users/me"), 2), 200).get("following_count").asInt()).isEqualTo(1);
        assertThat(data(get("/api/users/1"), 200).get("follower_count").asInt()).isEqualTo(1);
        for (int i = 0; i < 2; i++) assertThat(relations.unfollow(2L, 1L, null).followerCount()).isZero();
        assertThat(count("user_follow")).isEqualTo(1);
        mvc.perform(auth(put("/api/users/me/following/2"), 2)).andExpect(status().isBadRequest());
        mvc.perform(auth(put("/api/users/me/following/999"), 2)).andExpect(status().isNotFound());
        db.update("UPDATE account SET status=2 WHERE id=1");
        mvc.perform(auth(put("/api/users/me/following/1"), 2)).andExpect(status().isNotFound());
        mvc.perform(get("/api/users/me/following")).andExpect(status().isUnauthorized());
    }

    @Test
    void relationPagesIncludeMutualStateAndRefollowOrder() throws Exception {
        for (long id : List.of(2L, 3L, 4L)) relations.follow(1L, id, null);
        relations.follow(3L, 1L, null);
        db.update("UPDATE user_follow SET updated_at='2026-10-01 12:00:00'");
        var first = relations.list(1L, true, "", 2);
        assertThat(first.items()).extracting(x -> x.userId()).containsExactly(4L, 3L);
        assertThat(first.items()).allMatch(x -> x.following());
        assertThat(relations.list(1L, true, first.nextCursor(), 2).items()).extracting(x -> x.userId()).containsExactly(2L);
        assertThat(relations.list(1L, false, "", 20).items().getFirst().following()).isTrue();
        relations.unfollow(1L, 3L, null);
        assertThat(relations.list(1L, false, "", 20).items().getFirst().following()).isFalse();
        relations.unfollow(1L, 2L, null);
        relations.follow(1L, 2L, null);
        assertThat(relations.list(1L, true, "", 20).items().getFirst().userId()).isEqualTo(2);
        db.update("UPDATE account SET status=3 WHERE id=4");
        assertThat(relations.list(1L, true, "", 20).items()).extracting(x -> x.userId()).containsExactly(2L);
        data(auth(get("/api/users/me/followers"), 1), 200);
        mvc.perform(auth(get("/api/users/me/following").param("cursor", "bad"), 1)).andExpect(status().isBadRequest());
    }

    @Test
    void concurrentOppositeFollowsHaveNoDeadlocksOrDuplicateCounters() throws Exception {
        parallel(8, i -> relations.follow(i % 2 == 0 ? 1L : 2L, i % 2 == 0 ? 2L : 1L, null));
        assertThat(count("user_follow")).isEqualTo(2);
        assertThat(db.queryForObject("SELECT SUM(following_count) FROM user_relation_stat", Integer.class)).isEqualTo(2);
        parallel(8, i -> {
            if (i % 2 == 0) relations.follow(1L, 2L, null);
            else relations.unfollow(1L, 2L, null);
        });
        int active = db.queryForObject("SELECT COUNT(*) FROM user_follow WHERE user_id=1 AND status=1", Integer.class);
        assertThat(db.queryForObject("SELECT following_count FROM user_relation_stat WHERE user_id=1", Integer.class)).isEqualTo(active);
        assertThat(db.queryForObject("SELECT follower_count FROM user_relation_stat WHERE user_id=2", Integer.class)).isEqualTo(active);
    }

    @Test
    void relationRollbackAndMissingStatRecovery() {
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("injected"); })
                .when(relationRepository).changeCounts(1L, 2L, 1);
        assertThatThrownBy(() -> relations.follow(1L, 2L, null)).isInstanceOf(IllegalStateException.class);
        assertThat(count("user_follow")).isZero();
        assertThat(db.queryForObject("SELECT SUM(following_count+follower_count) FROM user_relation_stat", Integer.class)).isZero();
        doCallRealMethod().when(relationRepository).changeCounts(1L, 2L, 1);
        relations.follow(1L, 2L, null);
        db.update("DELETE FROM user_relation_stat WHERE user_id=1");
        assertThat(relations.follow(1L, 3L, null).followingCount()).isEqualTo(2);
    }

    @Test
    void followingFeedNeedsValidUnrevokedViewer() throws Exception {
        mvc.perform(get("/api/feed-items").param("scene", "following")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/feed-items").param("scene", "following").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        when(bucket.isExists()).thenReturn(true);
        mvc.perform(auth(get("/api/feed-items").param("scene", "following"), 3)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/feed-items")).andExpect(status().isOk());
    }

    @Test
    void followingFeedUsesLiveRelationsAndStablePagination() throws Exception {
        assertThat(data(auth(get("/api/feed-items").param("scene", "following"), 3), 200).get("items").size()).isZero();
        relations.follow(3L, 1L, null);
        JsonNode first = data(auth(get("/api/feed-items").param("scene", "following").param("limit", "1"), 3), 200);
        assertThat(first.get("items").get(0).get("video_id").asLong()).isEqualTo(2);
        assertThat(first.get("items").get(0).get("following").asBoolean()).isTrue();
        JsonNode second = data(auth(get("/api/feed-items").param("scene", "following").param("limit", "1")
                .param("cursor", first.get("next_cursor").asText()), 3), 200);
        assertThat(second.get("items").get(0).get("video_id").asLong()).isEqualTo(1);
        assertThat(second.get("has_more").asBoolean()).isFalse();
        assertThat(data(auth(get("/api/feed-items").param("scene", "following"), 4), 200).get("items").size()).isZero();
        db.update("INSERT INTO video(id,author_id,title,media_url,cover_url,published_at) VALUES (5,1,'new','m','c',CURRENT_TIMESTAMP)");
        assertThat(data(auth(get("/api/feed-items").param("scene", "following"), 3), 200).get("items").size()).isEqualTo(3);
        db.update("UPDATE video SET status=4 WHERE id=2");
        assertThat(data(auth(get("/api/feed-items").param("scene", "following"), 3), 200).get("items").size()).isEqualTo(2);
        relations.unfollow(3L, 1L, null);
        assertThat(data(auth(get("/api/feed-items").param("scene", "following"), 3), 200).get("items").size()).isZero();
        for (JsonNode item : data(auth(get("/api/feed-items"), 3), 200).get("items")) assertThat(item.get("following").asBoolean()).isFalse();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder req, long id) {
        return req.header("Authorization", "Bearer " + jwt.signAccessToken(id, "user"));
    }
    private JsonNode data(MockHttpServletRequestBuilder req, int statusCode) throws Exception {
        return json.readTree(mvc.perform(req).andExpect(status().is(statusCode)).andReturn().getResponse().getContentAsString()).get("data");
    }
    private int count(String table) { return db.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private int commentCount(long video) { return db.queryForObject("SELECT comment_count FROM video_stat WHERE video_id=?", Integer.class, video); }
    private void conflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMENT_REQUEST_CONFLICT));
    }
    private void parallel(int n, IntConsumer action) throws Exception {
        try (var pool = Executors.newFixedThreadPool(n)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int index = i;
                futures.add(pool.submit(() -> { try { start.await(); } catch (InterruptedException e) { throw new RuntimeException(e); } action.accept(index); }));
            }
            start.countDown();
            for (Future<?> future : futures) future.get(20, TimeUnit.SECONDS);
        }
    }
}
