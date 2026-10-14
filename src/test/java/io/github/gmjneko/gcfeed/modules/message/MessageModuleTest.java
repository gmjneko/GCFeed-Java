package io.github.gmjneko.gcfeed.modules.message;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.constant.RedisKeyConstant;
import io.github.gmjneko.gcfeed.common.jwt.JwtUtil;
import io.github.gmjneko.gcfeed.modules.interaction.service.CommentService;
import io.github.gmjneko.gcfeed.modules.interaction.service.InteractionService;
import io.github.gmjneko.gcfeed.modules.message.entity.UserMessage;
import io.github.gmjneko.gcfeed.modules.message.repository.MessageRepository;
import io.github.gmjneko.gcfeed.modules.relation.service.RelationService;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 真实 H2 与事务验证消息读写、幂等和提交后通知；Redis 用内存桶保存未读数。 */
@SpringBootTest
@AutoConfigureMockMvc
class MessageModuleTest {

    private static final String TOKEN = "gcfeed-dev-only-internal-token";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtUtil jwt;
    @Autowired
    private JsonMapper json;
    @Autowired
    private InteractionService interactions;
    @Autowired
    private CommentService comments;
    @Autowired
    private RelationService relations;
    @MockitoBean
    private RedissonClient redis;
    @MockitoSpyBean
    private MessageRepository messages;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void resetData() {
        doCallRealMethod().when(messages).insert(any());
        for (String table : java.util.List.of("user_message", "interaction_comment", "user_follow", "interaction_action",
                "video_stat", "video", "user_relation_stat", "account")) {
            jdbc.update("DELETE FROM " + table);
        }
        cache.clear();
        for (int id = 1; id <= 4; id++) {
            jdbc.update("INSERT INTO account(id, account, password, nickname, avatar_url) VALUES (?, ?, 'unused', ?, ?)",
                    id, "message-user-" + id, "用户" + id, "/avatar/" + id + ".png");
            jdbc.update("INSERT INTO user_relation_stat(user_id) VALUES (?)", id);
        }
        jdbc.update("""
                INSERT INTO video(id, author_id, title, media_url, cover_url, status, published_at)
                VALUES (1, 1, 'title', 'media', 'cover', 2, CURRENT_TIMESTAMP)
                """);
        jdbc.update("INSERT INTO video_stat(video_id) VALUES (1)");

        RBucket<Object> marker = mock(RBucket.class);
        when(redis.getBucket(anyString())).thenReturn(marker);
        when(marker.isExists()).thenReturn(false);
        when(marker.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);
        when(redis.getBucket(anyString(), any())).thenAnswer(invocation -> bucket(invocation.getArgument(0)));
    }

    @Test
    void listUsesTimeCursorAndHidesOtherUsersAndDeletedRows() throws Exception {
        insertMessage(1, "2026-10-01 10:00:00", 0, 0, "较早");
        insertMessage(1, "2026-10-03 10:00:00", 0, 0, "最新");
        insertMessage(1, "2026-10-02 10:00:00", 0, 0, "中间");
        insertMessage(2, "2026-10-04 10:00:00", 0, 0, "别人的");
        jdbc.update("""
                INSERT INTO user_message(user_id, type, title, content, is_read, deleted, created_at)
                VALUES (1, 'SYSTEM', '已删除', '隐藏', 0, 1, '2026-10-05 10:00:00')
                """);

        JsonNode first = data(auth(get("/api/messages").param("limit", "2"), 1), 200);
        assertThat(first.get("items")).hasSize(2);
        assertThat(first.get("items").get(0).get("title").asText()).isEqualTo("最新");
        assertThat(first.get("items").get(1).get("title").asText()).isEqualTo("中间");
        assertThat(first.get("has_more").asBoolean()).isTrue();
        String cursor = first.get("next_cursor").asText();
        assertThat(cursor).isNotBlank();

        JsonNode second = data(auth(get("/api/messages").param("cursor", cursor).param("limit", "2"), 1), 200);
        assertThat(second.get("items")).hasSize(1);
        assertThat(second.get("items").get(0).get("title").asText()).isEqualTo("较早");
        assertThat(second.get("has_more").asBoolean()).isFalse();
        assertThat(data(auth(get("/api/messages"), 2), 200).get("items")).hasSize(1);

        mvc.perform(get("/api/messages").param("cursor", "bad").header("Authorization", "Bearer " + jwt.signAccessToken(1L, "user")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CURSOR.getCode()));
        mvc.perform(get("/api/messages").param("limit", "0").header("Authorization", "Bearer " + jwt.signAccessToken(1L, "user")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(ErrorCode.INVALID_LIMIT.getCode()));
        mvc.perform(get("/api/messages")).andExpect(status().isUnauthorized());
    }

    @Test
    void markReadCountsTransitionsAndIgnoresForeignIds() throws Exception {
        long first = insertMessage(1, "2026-10-01 10:00:00", 0, 0, "一");
        long second = insertMessage(1, "2026-10-01 11:00:00", 0, 0, "二");
        long foreign = insertMessage(2, "2026-10-01 12:00:00", 0, 0, "别人");
        long already = insertMessage(1, "2026-10-01 09:00:00", 1, 0, "已读");

        JsonNode once = data(auth(patch("/api/messages").contentType("application/json")
                .content("{\"message_ids\":[" + first + "," + first + "," + foreign + "]}"), 1), 200);
        assertThat(once.get("updated_count").asInt()).isEqualTo(1);
        assertThat(unread(1)).isEqualTo(1);
        assertThat(isRead(second)).isZero();
        assertThat(isRead(foreign)).isZero();
        assertThat(readAt(already)).isNull();

        JsonNode again = data(auth(patch("/api/messages").contentType("application/json")
                .content("{\"message_ids\":[" + first + "]}"), 1), 200);
        assertThat(again.get("updated_count").asInt()).isZero();

        mvc.perform(auth(patch("/api/messages").contentType("application/json").content("{\"message_ids\":[0]}"), 1))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(ErrorCode.INVALID_MESSAGE_ID.getCode()));
        assertThat(isRead(second)).isZero();

        JsonNode all = data(auth(patch("/api/messages").contentType("application/json").content("{}"), 1), 200);
        assertThat(all.get("updated_count").asInt()).isEqualTo(1);
        assertThat(unread(1)).isZero();
        assertThat(data(auth(patch("/api/messages").contentType("application/json").content("{\"message_ids\":[]}"), 1), 200)
                .get("updated_count").asInt()).isZero();
    }

    @Test
    void internalCreateReplaysFirstContentAndRejectsInvalidInput() throws Exception {
        String body = """
                {"user_id":1,"type":"like","title":" 原文 ","content":" 内容 ",
                 "event_id":"event-1","actor_id":2,"actor_nickname":" 触发者 ","actor_avatar_url":" /a.png "}
                """;
        JsonNode created = data(internal(body, "key-1"), 201);
        assertThat(created.get("type").asText()).isEqualTo("LIKE");
        assertThat(created.get("title").asText()).isEqualTo("原文");
        assertThat(created.get("content").asText()).isEqualTo("内容");
        assertThat(created.get("actor_nickname").asText()).isEqualTo("触发者");
        assertThat(created.get("actor_avatar_url").asText()).isEqualTo("/a.png");
        assertThat(created.get("is_read").asBoolean()).isFalse();

        String replay = """
                {"user_id":1,"type":"SYSTEM","title":"新标题","content":"新内容",
                 "event_id":"event-1","actor_id":9,"actor_nickname":"后来者","actor_avatar_url":"/b.png"}
                """;
        JsonNode existing = data(internal(replay, "another-key"), 200);
        assertThat(existing.get("id").asLong()).isEqualTo(created.get("id").asLong());
        assertThat(existing.get("title").asText()).isEqualTo("原文");
        assertThat(existing.get("actor_nickname").asText()).isEqualTo("触发者");
        assertThat(count("user_message")).isEqualTo(1);

        JsonNode byKey = data(internal("""
                {"user_id":1,"type":"SYSTEM","title":"键原文","content":"键内容"}
                """, "same-key"), 201);
        JsonNode keyReplay = data(internal("""
                {"user_id":1,"type":"SYSTEM","title":"键新文","content":"键新内容","event_id":"other-event"}
                """, "same-key"), 200);
        assertThat(keyReplay.get("id").asLong()).isEqualTo(byKey.get("id").asLong());
        assertThat(keyReplay.get("title").asText()).isEqualTo("键原文");
        assertThat(keyReplay.get("event_id").isNull()).isTrue();

        data(internal("""
                {"user_id":1,"type":"SYSTEM","title":"空事件一","content":"一"}
                """, null), 201);
        data(internal("""
                {"user_id":1,"type":"SYSTEM","title":"空事件二","content":"二"}
                """, "  "), 201);
        assertThat(count("user_message")).isEqualTo(4);

        expect(internal("""
                {"user_id":1,"type":"OTHER","title":"标题","content":"内容"}
                """, null), 400, ErrorCode.INVALID_MESSAGE_TYPE);
        expect(internal("""
                {"user_id":1,"type":"SYSTEM","title":"  ","content":"内容"}
                """, null), 400, ErrorCode.EMPTY_MESSAGE_TITLE);
        expect(internal("""
                {"user_id":1,"type":"SYSTEM","title":"%s","content":"内容"}
                """.formatted("a".repeat(129)), null), 400, ErrorCode.MESSAGE_TITLE_TOO_LONG);
        expect(internal("""
                {"user_id":0,"type":"SYSTEM","title":"标题","content":"内容"}
                """, null), 400, ErrorCode.INVALID_USER_ID);
        expect(internal("""
                {"user_id":1,"type":"SYSTEM","title":"标题","content":"内容","event_id":"%s"}
                """.formatted("e".repeat(65)), null), 400, ErrorCode.EVENT_ID_TOO_LONG);
        mvc.perform(post("/internal/messages").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unreadCacheServesStaleValueUntilWriteOrReadEvictsIt() throws Exception {
        data(internal("""
                {"user_id":1,"type":"SYSTEM","title":"通知","content":"内容"}
                """, null), 201);
        assertThat(unread(1)).isEqualTo(1);
        String key = RedisKeyConstant.MESSAGE_UNREAD.formatted(1L);
        assertThat(cache.get(key)).isEqualTo("1");

        jdbc.update("UPDATE user_message SET is_read = 1 WHERE user_id = 1");
        assertThat(unread(1)).isEqualTo(1);

        data(auth(patch("/api/messages").contentType("application/json").content("{\"message_ids\":[]}"), 1), 200);
        assertThat(cache).doesNotContainKey(key);
        assertThat(unread(1)).isZero();
        assertThat(cache.get(key)).isEqualTo("0");
    }

    @Test
    void likeCommentAndFollowNotifyAfterCommitWithoutDuplicatingOrNotifyingSelf() {
        interactions.like(1L, 1L, null);
        interactions.favorite(2L, 1L, null);
        assertThat(count("user_message")).isZero();

        jdbc.update("UPDATE account SET nickname = ?, avatar_url = ? WHERE id = 2", "点赞者", "/liker.png");
        interactions.like(2L, 1L, null);
        interactions.unlike(2L, 1L, null);
        interactions.like(2L, 1L, null);
        assertThat(count("user_message")).isEqualTo(1);
        Map<String, Object> like = jdbc.queryForMap("SELECT * FROM user_message WHERE user_id = 1");
        assertThat(like.get("TYPE")).isEqualTo("LIKE");
        assertThat(like.get("TITLE")).isEqualTo("收到点赞");
        assertThat(like.get("CONTENT")).isEqualTo("点赞了你的视频");
        assertThat(like.get("EVENT_ID")).isEqualTo("interaction:like:1:2");
        assertThat(((Number) like.get("ACTOR_ID")).longValue()).isEqualTo(2L);
        assertThat(like.get("ACTOR_NICKNAME")).isEqualTo("点赞者");
        assertThat(like.get("ACTOR_AVATAR_URL")).isEqualTo("/liker.png");

        comments.create(1L, 1L, "自己的评论", null);
        var created = comments.create(2L, 1L, " 好看 ", "comment-key");
        comments.create(2L, 1L, " 好看 ", "comment-key");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_message WHERE type = 'COMMENT'", Integer.class)).isEqualTo(1);
        Map<String, Object> comment = jdbc.queryForMap("SELECT * FROM user_message WHERE type = 'COMMENT'");
        assertThat(comment.get("TITLE")).isEqualTo("收到评论");
        assertThat(comment.get("CONTENT")).isEqualTo("好看");
        assertThat(comment.get("EVENT_ID")).isEqualTo("interaction:comment:" + created.comment().id());

        relations.follow(2L, 1L, null);
        relations.unfollow(2L, 1L, null);
        relations.follow(2L, 1L, null);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_message WHERE type = 'FOLLOW'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT event_id FROM user_message WHERE type = 'FOLLOW'", String.class)).isEqualTo("relation:follow:1:2");
        assertThat(jdbc.queryForObject(
                "SELECT content FROM user_message WHERE type = 'FOLLOW'", String.class)).isEqualTo("关注了你");
    }

    @Test
    void notificationFailureDoesNotFailTheLike() {
        doThrow(new DataAccessResourceFailureException("down")).when(messages).insert(any());
        interactions.like(2L, 1L, null);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM interaction_action WHERE user_id = 2 AND action_type = 'LIKE' AND status = 1",
                Integer.class)).isEqualTo(1);
        assertThat(count("user_message")).isZero();
    }

    private long insertMessage(long userId, String createdAt, int read, int deleted, String title) {
        jdbc.update("""
                INSERT INTO user_message(user_id, type, title, content, is_read, deleted, created_at)
                VALUES (?, 'SYSTEM', ?, ?, ?, ?, ?)
                """, userId, title, title + "的内容", read, deleted, createdAt);
        return jdbc.queryForObject("SELECT MAX(id) FROM user_message", Long.class);
    }

    private int unread(long userId) throws Exception {
        return data(auth(get("/api/message-stats/unread"), userId), 200).get("unread_count").asInt();
    }

    private int isRead(long id) {
        return jdbc.queryForObject("SELECT is_read FROM user_message WHERE id = ?", Integer.class, id);
    }

    private Object readAt(long id) {
        return jdbc.queryForObject("SELECT read_at FROM user_message WHERE id = ?", Object.class, id);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, long userId) {
        return request.header("Authorization", "Bearer " + jwt.signAccessToken(userId, "user"));
    }

    private MockHttpServletRequestBuilder internal(String body, String key) {
        MockHttpServletRequestBuilder request = post("/internal/messages").contentType("application/json").content(body)
                .header("X-Internal-Token", TOKEN);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return request;
    }

    private JsonNode data(MockHttpServletRequestBuilder request, int status) throws Exception {
        MvcResult result = mvc.perform(request).andExpect(status().is(status)).andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private void expect(MockHttpServletRequestBuilder request, int status, ErrorCode error) throws Exception {
        mvc.perform(request).andExpect(status().is(status)).andExpect(jsonPath("$.code").value(error.getCode()));
    }

    @SuppressWarnings("unchecked")
    private RBucket<String> bucket(String key) {
        RBucket<String> bucket = mock(RBucket.class);
        when(bucket.get()).thenAnswer(invocation -> cache.get(key));
        doAnswer(invocation -> {
            cache.put(key, invocation.getArgument(0));
            return null;
        }).when(bucket).set(anyString(), any(Duration.class));
        when(bucket.delete()).thenAnswer(invocation -> cache.remove(key) != null);
        when(bucket.isExists()).thenAnswer(invocation -> cache.containsKey(key));
        when(bucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);
        return bucket;
    }
}
