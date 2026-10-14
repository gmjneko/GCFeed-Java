package io.github.gmjneko.gcfeed.modules.feed;

import io.github.gmjneko.gcfeed.common.jwt.JwtUtil;
import io.github.gmjneko.gcfeed.modules.embedding.service.VideoPublishedEvent;
import io.github.gmjneko.gcfeed.modules.feed.following.MemoryFollowingIndex;
import io.github.gmjneko.gcfeed.modules.feed.following.RelationIndexService;
import io.github.gmjneko.gcfeed.modules.feed.following.VideoFanoutService;
import io.github.gmjneko.gcfeed.modules.mq.RabbitSettings;
import io.github.gmjneko.gcfeed.modules.mq.RelationChangedEvent;
import io.github.gmjneko.gcfeed.modules.relation.repository.RelationRepository;
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
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FollowingFanoutTest.IndexConfiguration.class)
class FollowingFanoutTest {

    private static final LocalDateTime PUBLISHED = LocalDateTime.parse("2026-10-11T12:00:00");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private JwtUtil jwt;
    @Autowired
    private VideoFanoutService fanout;
    @Autowired
    private RelationIndexService relationIndex;
    @Autowired
    private MemoryFollowingIndex index;
    @Autowired
    private SqlRecorder sql;
    @MockitoBean
    private RedissonClient redis;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void reset() {
        for (String table : List.of("user_follow", "user_relation_stat", "video_stat", "video", "account")) {
            jdbc.update("DELETE FROM " + table);
        }
        index.clear();
        sql.statements.clear();
        RBucket<Object> bucket = mock(RBucket.class);
        when(redis.getBucket(anyString())).thenReturn(bucket);
        when(bucket.isExists()).thenReturn(false);
        when(bucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);
        account(1);
        account(2);
        account(3);
    }

    @Test
    void normalAuthorIsPushedAndBigVOnlyWritesOutbox() throws Exception {
        video(10, 1, PUBLISHED);
        follow(2, 1);
        follow(3, 1);
        stat(1, 2);
        fanout.onPublished(event(10, 1));
        fanout.onPublished(event(10, 1));
        assertThat(index.inboxSize(2)).isEqualTo(1);
        assertThat(index.inboxSize(3)).isEqualTo(1);
        assertThat(index.outboxSize(1)).isZero();
        assertThat(ids(feed(2))).containsExactly(10L);

        stat(1, 10000);
        video(11, 1, PUBLISHED.plusMinutes(1));
        fanout.onPublished(event(11, 1));
        assertThat(index.outboxSize(1)).isEqualTo(1);
        assertThat(index.inboxSize(2)).isEqualTo(1);
        assertThat(ids(feed(2))).containsExactly(11L, 10L);
    }

    @Test
    void followBackfillsRecentVideosAndUnfollowRemovesThem() throws Exception {
        video(10, 1, PUBLISHED);
        video(11, 1, PUBLISHED.plusMinutes(1));
        stat(1, 1);
        follow(2, 1);
        relationIndex.onChanged(relation(RelationChangedEvent.FOLLOW, 2, 1));
        assertThat(index.inboxSize(2)).isEqualTo(2);
        assertThat(ids(feed(2))).containsExactly(11L, 10L);

        jdbc.update("UPDATE user_follow SET status = 2 WHERE user_id = 2 AND target_user_id = 1");
        relationIndex.onChanged(relation(RelationChangedEvent.UNFOLLOW, 2, 1));
        assertThat(index.inboxSize(2)).isZero();
    }

    @Test
    void missingIndexFallsBackToDatabase() throws Exception {
        video(10, 1, PUBLISHED);
        follow(2, 1);
        stat(1, 1);
        sql.statements.clear();
        assertThat(ids(feed(2))).containsExactly(10L);
        assertThat(sql.statements).anyMatch(statement -> statement.contains("exists") && statement.contains("user_follow"));
    }

    private JsonNode feed(long userId) throws Exception {
        String body = mvc.perform(get("/api/feed-items").param("scene", "following")
                        .header("Authorization", "Bearer " + jwt.signAccessToken(userId, "user")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data");
    }

    private List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("video_id").asLong()));
        return ids;
    }

    private void account(long id) {
        jdbc.update("INSERT INTO account (id, account, password, nickname) VALUES (?, ?, 'unused', ?)",
                id, "fanout-" + id, "user-" + id);
    }

    private void video(long id, long authorId, LocalDateTime publishedAt) {
        jdbc.update("""
                INSERT INTO video (id, author_id, title, media_url, cover_url, status, published_at)
                VALUES (?, ?, 'title', '/v.mp4', '/c.jpg', 2, ?)
                """, id, authorId, publishedAt);
    }

    private void follow(long userId, long authorId) {
        jdbc.update("INSERT INTO user_follow (user_id, target_user_id, status) VALUES (?, ?, 1)", userId, authorId);
    }

    private void stat(long userId, int followers) {
        jdbc.update("DELETE FROM user_relation_stat WHERE user_id = ?", userId);
        jdbc.update("INSERT INTO user_relation_stat (user_id, following_count, follower_count) VALUES (?, 0, ?)",
                userId, followers);
    }

    private VideoPublishedEvent event(long videoId, long authorId) {
        return new VideoPublishedEvent("event-" + videoId, videoId, authorId, "title", "desc", Instant.parse("2026-10-11T04:00:00Z"));
    }

    private RelationChangedEvent relation(String action, long userId, long authorId) {
        return new RelationChangedEvent("relation-" + action, action, userId, authorId, Instant.parse("2026-10-11T04:00:00Z"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class IndexConfiguration {
        @Bean
        @Primary
        MemoryFollowingIndex memoryFollowingIndex(RelationRepository relations, RabbitSettings settings) {
            return new MemoryFollowingIndex(relations, settings);
        }

        @Bean
        SqlRecorder sqlRecorder() {
            return new SqlRecorder();
        }
    }

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
