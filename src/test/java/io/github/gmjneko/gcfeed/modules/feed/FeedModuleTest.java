package io.github.gmjneko.gcfeed.modules.feed;

import io.github.gmjneko.gcfeed.common.jwt.JwtUtil;
import io.github.gmjneko.gcfeed.modules.feed.model.FeedCursor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** H2 执行真实 MyBatis-Plus SQL；Redis 仅模拟现有 token 黑名单。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FeedModuleTest.SqlConfiguration.class)
class FeedModuleTest {
    private static final LocalDateTime TIME = LocalDateTime.parse("2026-10-11T12:34:56");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private SqlRecorder sqlRecorder;
    @MockitoBean
    private RedissonClient redis;

    private RBucket<Object> blacklist;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void resetData() {
        for (String table : List.of("interaction_comment", "user_follow", "interaction_action", "video_stat", "video", "user_relation_stat", "account")) {
            jdbc.update("DELETE FROM " + table);
        }
        for (long id : List.of(1L, 2L, 9L, 10L)) {
            jdbc.update("INSERT INTO account (id, account, password, nickname, avatar_url) VALUES (?, ?, ?, ?, ?)",
                    id, "feed-user-" + id, "unused-hash", "author-" + id, "/avatar/" + id + ".png");
        }
        blacklist = mock(RBucket.class);
        when(redis.getBucket(anyString())).thenReturn(blacklist);
        when(blacklist.isExists()).thenReturn(false);
        sqlRecorder.statements.clear();
    }

    @Test
    void emptyPageAndSceneNormalizationFollowContract() throws Exception {
        for (String scene : List.of("", " \t ", " Timeline ", "TIMELINE")) {
            JsonNode page = request(get("/api/feed-items").param("scene", scene).param("cursor", " "));
            assertThat(page.get("scene").asText()).isEqualTo("timeline");
            assertThat(page.get("items").isArray()).isTrue();
            assertThat(page.get("items").size()).isZero();
            assertThat(page.get("next_cursor").asText()).isEmpty();
            assertThat(page.get("has_more").asBoolean()).isFalse();
        }
        for (String scene : List.of("hot", "unknown")) {
            mvc.perform(get("/api/feed-items").param("scene", scene))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(70003));
        }
    }

    @Test
    void limitsAndInvalidCursorsAreValidated() throws Exception {
        for (int id = 1; id <= 101; id++) {
            video(id, 1, TIME);
        }
        assertThat(request(get("/api/feed-items")).get("items").size()).isEqualTo(10);
        for (String limit : List.of("100", "101", "2147483647")) {
            JsonNode page = request(get("/api/feed-items").param("limit", limit));
            assertThat(page.get("items").size()).isEqualTo(100);
            assertThat(page.get("has_more").asBoolean()).isTrue();
            JsonNode last = request(get("/api/feed-items").param("limit", limit)
                    .param("cursor", page.get("next_cursor").asText()));
            assertThat(ids(last)).containsExactly(1L);
            assertThat(last.get("has_more").asBoolean()).isFalse();
            assertThat(last.get("next_cursor").asText()).isNotEmpty();
        }
        for (String limit : List.of("0", "-1")) {
            mvc.perform(get("/api/feed-items").param("limit", limit))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(70001));
        }
        for (String limit : List.of("abc", "2147483648")) {
            mvc.perform(get("/api/feed-items").param("limit", limit))
                    .andExpect(status().isBadRequest());
        }
        for (String cursor : List.of("bad!", "YmFkLXRpbWV8MQ", "MjAyNi0xMC0xMVQxMjowMHww")) {
            mvc.perform(get("/api/feed-items").param("cursor", cursor))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(70002));
        }
    }

    @Test
    void timelineFiltersVisibilityAndPaginatesTiesWithoutDuplicatesOrOmissions() throws Exception {
        video(1, 1, TIME);
        video(2, 2, TIME.plusDays(1));
        video(3, 1, TIME.plusDays(1));
        video(4, 2, TIME.minusDays(1));
        video(5, 1, TIME);
        video(6, 2, TIME.plusDays(2));
        for (int id = 7; id <= 10; id++) {
            video(id, 1, TIME.plusDays(3));
        }
        jdbc.update("UPDATE video SET status = 1 WHERE id = 7");
        jdbc.update("UPDATE video SET status = 3 WHERE id = 8");
        jdbc.update("UPDATE video SET status = 4 WHERE id = 9");
        jdbc.update("UPDATE video SET published_at = NULL WHERE id = 10");

        List<Long> allIds = new ArrayList<>();
        String cursor = "";
        for (int pageIndex = 0; pageIndex < 3; pageIndex++) {
            JsonNode page = request(get("/api/feed-items").param("limit", "2").param("cursor", cursor));
            List<Long> pageIds = ids(page);
            assertThat(pageIds).hasSize(2);
            allIds.addAll(pageIds);
            cursor = page.get("next_cursor").asText();
            assertThat(FeedCursor.decode(cursor).videoId()).isEqualTo(pageIds.getLast());
            assertThat(page.get("has_more").asBoolean()).isEqualTo(pageIndex < 2);
        }
        assertThat(allIds).containsExactly(6L, 3L, 2L, 5L, 1L, 4L);
        JsonNode end = request(get("/api/feed-items").param("limit", "2").param("cursor", cursor));
        assertThat(ids(end)).isEmpty();
        assertThat(end.get("next_cursor").asText()).isEmpty();
        assertThat(end.get("has_more").asBoolean()).isFalse();
    }

    @Test
    void insertionAndDeletionBetweenRequestsDoNotShiftTheCursor() throws Exception {
        for (int id = 1; id <= 5; id++) {
            video(id, 1, TIME);
        }
        JsonNode first = request(get("/api/feed-items").param("limit", "2"));
        assertThat(ids(first)).containsExactly(5L, 4L);
        video(6, 1, TIME.plusSeconds(1));
        jdbc.update("UPDATE video SET status = 4 WHERE id = 4");
        JsonNode second = request(get("/api/feed-items").param("limit", "2")
                .param("cursor", first.get("next_cursor").asText()));
        assertThat(ids(second)).containsExactly(3L, 2L);
        JsonNode third = request(get("/api/feed-items").param("limit", "2")
                .param("cursor", second.get("next_cursor").asText()));
        assertThat(ids(third)).containsExactly(1L);
        assertThat(third.get("has_more").asBoolean()).isFalse();
    }

    @Test
    void cardsIncludePublicFieldsAndHandleMissingOrCancelledRelations() throws Exception {
        video(1, 1, TIME);
        video(2, 2, TIME);
        video(3, 999, TIME);
        jdbc.update("UPDATE account SET status = 3 WHERE id = 2");
        jdbc.update("INSERT INTO video_stat (video_id, like_count, comment_count, favorite_count) VALUES (1, 18, 4, 7)");

        JsonNode page = request(get("/api/feed-items"));
        assertThat(ids(page)).containsExactly(3L, 2L, 1L);
        JsonNode card = item(page, 1);
        assertThat(card.get("author_id").asLong()).isEqualTo(1);
        assertThat(card.get("author_nickname").asText()).isEqualTo("author-1");
        assertThat(card.get("author_avatar_url").asText()).isEqualTo("/avatar/1.png");
        assertThat(card.get("title").asText()).isEqualTo("video-1");
        assertThat(card.get("description").asText()).isEqualTo("description-1");
        assertThat(card.get("media_url").asText()).isEqualTo("/video/1.mp4");
        assertThat(card.get("cover_url").asText()).isEqualTo("/cover/1.jpg");
        assertThat(card.get("published_at").asText()).isEqualTo("2026-10-11 12:34:56");
        assertThat(card.get("like_count").asInt()).isEqualTo(18);
        assertThat(card.get("comment_count").asInt()).isEqualTo(4);
        assertThat(card.get("favorite_count").asInt()).isEqualTo(7);
        assertThat(card.has("account")).isFalse();
        assertThat(card.has("password")).isFalse();
        for (long id : List.of(2L, 3L)) {
            JsonNode incomplete = item(page, id);
            assertThat(incomplete.get("author_nickname").isNull()).isTrue();
            assertThat(incomplete.get("author_avatar_url").isNull()).isTrue();
            assertThat(incomplete.get("like_count").asInt()).isZero();
            assertThat(incomplete.get("comment_count").asInt()).isZero();
            assertThat(incomplete.get("favorite_count").asInt()).isZero();
        }
    }

    @Test
    void viewerActionsAreActiveAndIsolatedByUser() throws Exception {
        for (int id = 1; id <= 4; id++) {
            video(id, 1, TIME);
        }
        action(9, 1, "LIKE", 1);
        action(9, 1, "FAVORITE", 1);
        action(9, 2, "LIKE", 2);
        action(9, 2, "FAVORITE", 2);
        action(10, 3, "LIKE", 1);
        action(10, 3, "FAVORITE", 1);
        action(9, 4, "UNKNOWN", 1);

        JsonNode viewer = request(authenticated(9));
        assertActions(item(viewer, 1), true, true);
        for (long id : List.of(2L, 3L, 4L)) {
            assertActions(item(viewer, id), false, false);
        }
        JsonNode other = request(authenticated(10));
        assertActions(item(other, 1), false, false);
        assertActions(item(other, 3), true, true);

        for (MockHttpServletRequestBuilder req : List.of(get("/api/feed-items"),
                get("/api/feed-items").header("Authorization", "Bearer invalid-token"))) {
            for (JsonNode card : request(req).get("items")) {
                assertActions(card, false, false);
            }
        }
        when(blacklist.isExists()).thenReturn(true);
        for (JsonNode card : request(authenticated(9)).get("items")) {
            assertActions(card, false, false);
        }
    }

    @Test
    void sqlCountIsBoundedAndThereIsNoCountQueryOrDeepOffset() throws Exception {
        for (int id = 1; id <= 101; id++) {
            video(id, id % 2 + 1, TIME);
        }
        for (int limit : List.of(1, 10, 100)) {
            sqlRecorder.statements.clear();
            JsonNode page = request(get("/api/feed-items").param("limit", String.valueOf(limit)));
            assertThat(sqlRecorder.statements).hasSize(3);
            assertSqlShape(false);
            sqlRecorder.statements.clear();
            request(authenticated(9).param("limit", String.valueOf(limit)));
            assertThat(sqlRecorder.statements).hasSize(5);
            assertSqlShape(true);

            sqlRecorder.statements.clear();
            request(get("/api/feed-items").param("limit", "1")
                    .param("cursor", page.get("next_cursor").asText()));
            assertThat(sqlRecorder.statements).hasSize(3);
            assertSqlShape(false);
        }
        jdbc.update("DELETE FROM video");
        sqlRecorder.statements.clear();
        request(authenticated(9));
        assertThat(sqlRecorder.statements).hasSize(1);
    }

    @Test
    void followingUsesBoundedQueriesWithoutLoadingAllFollowedIds() throws Exception {
        jdbc.update("INSERT INTO user_follow(user_id,target_user_id,status) VALUES (9,1,1),(9,2,1)");
        for (int id = 1; id <= 101; id++) video(id, id % 2 + 1, TIME);
        for (int limit : List.of(1, 10, 100)) {
            sqlRecorder.statements.clear();
            JsonNode page = request(authenticated(9).param("scene", "following").param("limit", String.valueOf(limit)));
            assertThat(page.get("items").size()).isEqualTo(limit);
            assertThat(sqlRecorder.statements).hasSize(5);
            assertThat(sqlRecorder.statements.getFirst()).contains("exists", "user_follow", "limit ?");
            assertThat(sqlRecorder.statements).allSatisfy(sql -> assertThat(sql).doesNotContain("count(", "offset"));
        }
    }

    private void assertSqlShape(boolean authenticated) {
        assertThat(sqlRecorder.statements).allSatisfy(sql -> {
            assertThat(sql).doesNotContain("count(", "count (", "offset");
        });
        assertThat(sqlRecorder.statements.getFirst()).contains("order by published_at desc,id desc", "limit ?");
        assertThat(sqlRecorder.statements.stream().filter(sql -> sql.contains("interaction_action")).count())
                .isEqualTo(authenticated ? 1 : 0);
    }

    private void video(long id, long authorId, LocalDateTime time) {
        jdbc.update("""
                INSERT INTO video (id, author_id, title, description, media_url, cover_url, published_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, authorId, "video-" + id, "description-" + id, "/video/" + id + ".mp4",
                "/cover/" + id + ".jpg", time);
    }

    private void action(long userId, long videoId, String type, int status) {
        jdbc.update("INSERT INTO interaction_action (user_id, video_id, action_type, status) VALUES (?, ?, ?, ?)",
                userId, videoId, type, status);
    }

    private MockHttpServletRequestBuilder authenticated(long userId) {
        return get("/api/feed-items").header("Authorization", "Bearer " + jwtUtil.signAccessToken(userId, "user"));
    }

    private JsonNode request(MockHttpServletRequestBuilder req) throws Exception {
        String body = mvc.perform(req).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.message").value("ok"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data");
    }

    private List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("items").forEach(card -> ids.add(card.get("video_id").asLong()));
        return ids;
    }

    private JsonNode item(JsonNode page, long id) {
        for (JsonNode card : page.get("items")) {
            if (card.get("video_id").asLong() == id) {
                return card;
            }
        }
        throw new AssertionError("Missing video " + id);
    }

    private void assertActions(JsonNode card, boolean liked, boolean favorited) {
        assertThat(card.get("liked").asBoolean()).isEqualTo(liked);
        assertThat(card.get("favorited").asBoolean()).isEqualTo(favorited);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfiguration {
        @Bean
        SqlRecorder sqlRecorder() {
            return new SqlRecorder();
        }
    }

    /** 记录真正提交给 JDBC 的 SQL，包含分页插件可能生成的 count。 */
    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    static class SqlRecorder implements Interceptor {
        final List<String> statements = new ArrayList<>();

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            StatementHandler handler = (StatementHandler) invocation.getTarget();
            statements.add(handler.getBoundSql().getSql().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "));
            return invocation.proceed();
        }
    }
}
