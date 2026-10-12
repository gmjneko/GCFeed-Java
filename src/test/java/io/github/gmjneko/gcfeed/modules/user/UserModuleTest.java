package io.github.gmjneko.gcfeed.modules.user;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.context.UserContext;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.common.jwt.JwtPayload;
import io.github.gmjneko.gcfeed.common.jwt.JwtUtil;
import io.github.gmjneko.gcfeed.modules.user.dto.RegisterDTO;
import io.github.gmjneko.gcfeed.modules.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证 HTTP、鉴权、真实 Mapper 和事务链路；仅用可控的 Redis 客户端替身隔离外部服务。 */
@SpringBootTest
@AutoConfigureMockMvc
class UserModuleTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserService userService;
    @Autowired
    private JwtUtil jwtUtil;
    @MockitoBean
    private RedissonClient redis;

    private final Map<String, RBucket<Object>> buckets = new ConcurrentHashMap<>();
    private final Map<String, Boolean> revoked = new ConcurrentHashMap<>();

    @BeforeEach
    void resetData() {
        jdbc.update("DELETE FROM video");
        jdbc.update("DELETE FROM user_relation_stat");
        jdbc.update("DELETE FROM account");
        buckets.clear();
        revoked.clear();
        when(redis.getBucket(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return buckets.computeIfAbsent(key, ignored -> {
                @SuppressWarnings("unchecked")
                RBucket<Object> bucket = mock(RBucket.class);
                when(bucket.isExists()).thenAnswer(call -> revoked.containsKey(key));
                when(bucket.setIfAbsent(eq("1"), any(Duration.class)))
                        .thenAnswer(call -> revoked.putIfAbsent(key, true) == null);
                return bucket;
            });
        });
    }

    @Test
    void registerAndLoginPreserveContractAndNeverExposePassword() throws Exception {
        JsonNode profile = register(" alice ", " secret ", " 爱丽丝 ");
        long id = profile.get("id").asLong();
        assertThat(profile.get("account").asText()).isEqualTo("alice");
        assertThat(profile.get("nickname").asText()).isEqualTo("爱丽丝");
        assertThat(profile.get("role").asText()).isEqualTo("user");
        assertThat(profile.get("status").asInt()).isEqualTo(1);
        assertThat(profile.get("following_count").asLong()).isZero();
        assertThat(profile.has("password")).isFalse();
        String hash = jdbc.queryForObject("SELECT password FROM account WHERE id = ?", String.class, id);
        assertThat(hash).startsWith("$2").isNotEqualTo("secret");
        assertThat(passwordEncoder.matches("secret", hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_relation_stat WHERE user_id = ?",
                Integer.class, id)).isEqualTo(1);
        JsonNode token = data(login(" alice ", " secret ").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token_type").value("Bearer"))
                .andExpect(jsonPath("$.data.expires_in_seconds").value(900)));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token.get("access_token").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.password").doesNotExist());
        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    void duplicateRegistrationReturnsConflictAndDoesNotModifyExistingAccount() throws Exception {
        register("alice", "secret", "first");
        registerRequest(" alice ", "different", "second")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(10011));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_relation_stat", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT nickname FROM account", String.class)).isEqualTo("first");
    }

    @Test
    void concurrentRegistrationsCreateExactlyOneAccountAndOneStat() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var calls = List.of(executor.submit(() -> concurrentRegister(start)),
                    executor.submit(() -> concurrentRegister(start)));
            start.countDown();
            assertThat(List.of(calls.get(0).get(15, TimeUnit.SECONDS), calls.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(0, 10011);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_relation_stat", Integer.class)).isEqualTo(1);
    }

    @Test
    void registrationRollsBackWhenStatInitializationFails() {
        // 注入真实数据库约束失败，验证不是只有方法上写了 @Transactional。
        jdbc.execute("ALTER TABLE user_relation_stat ADD CONSTRAINT test_stat_failure CHECK (following_count > 0)");
        try {
            assertThatThrownBy(() -> userService.register(new RegisterDTO("rollback", "secret", "name")))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account", Integer.class)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE user_relation_stat DROP CONSTRAINT test_stat_failure");
        }
    }

    @Test
    void invalidCredentialsAreIndistinguishableAndAbnormalAccountsCannotLogin() throws Exception {
        long id = register("alice", "secret", "name").get("id").asLong();
        for (String account : List.of("alice", "unknown")) {
            login(account, "wrong").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(10012));
        }
        for (int accountStatus : List.of(2, 3)) {
            jdbc.update("UPDATE account SET status = ? WHERE id = ?", accountStatus, id);
            login("alice", "secret").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(10013));
            login("alice", "wrong").andExpect(status().isUnauthorized());
        }
        String token = jwtUtil.signAccessToken(id, "user");
        mvc.perform(get("/api/users/" + id)).andExpect(status().isNotFound());
        patchProfile(token, "{\"bio\":\"after cancel\"}").andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT bio FROM account WHERE id = ?", String.class, id)).isNull();
        registerRequest("alice", "secret", "again").andExpect(status().isConflict());
    }

    @Test
    void profilePatchPreservesOmittedFieldsSupportsClearingAndCanBeRepeated() throws Exception {
        long id = register("alice", "secret", "original").get("id").asLong();
        String token = token("alice", "secret");
        patchProfile(token, "{\"avatar_url\":\" /avatar.png \",\"bio\":\" old bio \"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.avatar_url").value("/avatar.png"));
        String body = "{\"nickname\":\" new name \",\"avatar_url\":null,\"account\":\"hacked\",\"role\":\"admin\"}";
        for (int i = 0; i < 2; i++) {
            patchProfile(token, body).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nickname").value("new name"))
                    .andExpect(jsonPath("$.data.avatar_url").value("/avatar.png"))
                    .andExpect(jsonPath("$.data.bio").value("old bio"))
                    .andExpect(jsonPath("$.data.account").value("alice"))
                    .andExpect(jsonPath("$.data.role").value("user"));
        }
        patchProfile(token, "{\"avatar_url\":\"\",\"bio\":\"   \"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.avatar_url").value(""))
                .andExpect(jsonPath("$.data.bio").value(""));
        assertThat(jdbc.queryForObject("SELECT nickname FROM account WHERE id = ?", String.class, id))
                .isEqualTo("new name");
        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    void invalidUpdatesAndInputLengthsReturnBadRequestWithoutWrites() throws Exception {
        register("alice", "secret", "name");
        String token = token("alice", "secret");
        patchProfile(token, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(10009));
        patchProfile(token, "{\"nickname\":null}").andExpect(status().isBadRequest());
        patchProfile(token, "{\"nickname\":\"  \"}").andExpect(status().isBadRequest());
        for (var entry : Map.of("nickname", 65, "avatar_url", 513, "bio", 256).entrySet()) {
            patchProfile(token, json.writeValueAsString(Map.of(entry.getKey(), "x".repeat(entry.getValue()))))
                    .andExpect(status().isBadRequest());
        }
        registerRequest("x".repeat(65), "secret", "name").andExpect(status().isBadRequest());
        registerRequest("longpass", "x".repeat(65), "name").andExpect(status().isBadRequest());
        // 25 个汉字只占 25 字符，但已经超过 BCrypt 的 72 字节上限。
        registerRequest("utf8pass", "密".repeat(25), "name").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10005));
        login("alice", "密".repeat(25)).andExpect(status().isBadRequest());
        registerRequest("utf8boundary", "密".repeat(24), "name").andExpect(status().isCreated());
        login("utf8boundary", "密".repeat(24)).andExpect(status().isOk());
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest());
        registerRequest(" ", "secret", "name").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT nickname FROM account WHERE account = 'alice'", String.class))
                .isEqualTo("name");
    }

    @Test
    void publicProfileHidesIdentityAndCountsOnlyPublishedWorks() throws Exception {
        long id = register("alice", "secret", "name").get("id").asLong();
        jdbc.update("UPDATE user_relation_stat SET following_count = 3, follower_count = 8 WHERE user_id = ?", id);
        for (int videoStatus : List.of(1, 2, 3, 4)) {
            jdbc.update("INSERT INTO video(author_id, title, media_url, cover_url, status) VALUES (?, 't', 'm', 'c', ?)",
                    id, videoStatus);
        }
        mvc.perform(get("/api/users/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.following_count").value(3))
                .andExpect(jsonPath("$.data.follower_count").value(8))
                .andExpect(jsonPath("$.data.work_count").value(1))
                .andExpect(jsonPath("$.data.account").doesNotExist())
                .andExpect(jsonPath("$.data.role").doesNotExist())
                .andExpect(jsonPath("$.data.status").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());
        jdbc.update("DELETE FROM user_relation_stat WHERE user_id = ?", id);
        mvc.perform(get("/api/users/" + id)).andExpect(jsonPath("$.data.following_count").value(0))
                .andExpect(jsonPath("$.data.follower_count").value(0));
    }

    @Test
    void missingUsersAndInvalidIdsUseCorrectStatuses() throws Exception {
        mvc.perform(get("/api/users/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(10010));
        for (String id : List.of("0", "-1", "abc", "999999999999999999999999")) {
            mvc.perform(get("/api/users/" + id)).andExpect(status().isBadRequest());
        }
        String token = jwtUtil.signAccessToken(999999L, "user");
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        patchProfile(token, "{\"bio\":\"hello\"}").andExpect(status().isNotFound());
    }

    @Test
    void protectedRoutesRejectMissingInvalidAndRevokedTokens() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        patchProfile("broken", "{\"bio\":\"x\"}").andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/sessions/current")).andExpect(status().isUnauthorized());
        register("alice", "secret", "name");
        String token = token("alice", "secret");
        String otherToken = token("alice", "secret");
        mvc.perform(delete("/api/sessions/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        JwtPayload payload = jwtUtil.parseAccessToken(token);
        RBucket<Object> bucket = buckets.get("auth:blacklist:v1:" + payload.getJwtId());
        var ttl = org.mockito.ArgumentCaptor.forClass(Duration.class);
        verify(bucket).setIfAbsent(eq("1"), ttl.capture());
        assertThat(ttl.getValue().toSeconds()).isBetween(1L, 900L);
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/sessions/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk());
        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    void redisFailuresCannotResurrectRevokedTokensOrReportSuccessfulLogout() throws Exception {
        register("alice", "secret", "name");
        String token = token("alice", "secret");
        when(redis.getBucket(anyString())).thenThrow(new IllegalStateException("Redis unavailable"));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.data").value(nullValue()));
        mvc.perform(delete("/api/sessions/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError());
        assertThat(UserContext.getUserId()).isNull();
    }

    private int concurrentRegister(CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            userService.register(new RegisterDTO("concurrent", "secret", "name"));
            return 0;
        } catch (BusinessException ex) {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_ALREADY_EXISTS);
            return ex.getErrorCode().getCode();
        }
    }

    private ResultActions registerRequest(String account, String password, String nickname) throws Exception {
        return mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("account", account, "password", password, "nickname", nickname))));
    }

    private JsonNode register(String account, String password, String nickname) throws Exception {
        return data(registerRequest(account, password, nickname).andExpect(status().isCreated()));
    }

    private ResultActions login(String account, String password) throws Exception {
        return mvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("account", account, "password", password))));
    }

    private String token(String account, String password) throws Exception {
        return data(login(account, password).andExpect(status().isOk())).get("access_token").asText();
    }

    private ResultActions patchProfile(String token, String body) throws Exception {
        return mvc.perform(patch("/api/users/me").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

}
